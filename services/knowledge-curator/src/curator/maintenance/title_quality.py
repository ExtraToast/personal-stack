"""Periodic re-titling of promoted notes whose title shape is poor.

The curator's initial classification prompt nudges the model toward
3-8-word declarative titles ("Vault Raft unseal requires Shamir
keys") and away from question / how-to framings ("How does Vault
Raft unseal work?"). Older promotions and stale `_inbox/_needs-
review/` drains pre-date the prompt tightening and still carry the
framing-word shapes. This pass walks promoted notes whose title
matches a configurable regex set, asks the heavy model for a
better title, and rewrites both the DB row and the vault file's
frontmatter.

Single-shot batch: invoke ``python -m curator.maintenance.title_quality``
on a CronJob schedule. ``KB_TITLE_BATCH_SIZE`` (default 25) caps the
candidate count per pass; the next pass picks up whatever is left.
Failures are logged + skipped — the next pass retries them.

Constraints:

- Only promoted notes (``vault_path IS NOT NULL``) are eligible. The
  inbox curator still owns drafts.
- Re-titling does NOT change the ULID-derived slug on disk — keeping
  the vault filename stable preserves git history and any inbound
  ``[[wikilinks]]``. Only the in-file frontmatter ``title`` field and
  the ``kb_notes.title`` column update.
- Reuses ``ollama_router.resolve_chat`` so the pass routes to the
  rx7900xtx heavy endpoint when available and silently falls back to
  the in-cluster CPU Ollama when offline.
"""

from __future__ import annotations

import json
import logging
import os
import sys
from dataclasses import dataclass
from pathlib import Path

import httpx
import structlog
from pydantic import BaseModel, Field, ValidationError

from curator import telemetry
from curator.ollama_router import resolve_chat
from curator.settings import Settings
from curator.store import CuratorStore, PostgresCuratorStore
from curator.vault import (
    CuratorVault,
    parse_note_file,
    render_note_file,
)

log = structlog.get_logger(__name__)

# Default framing-word prefixes that the classifier prompt explicitly
# tells the model to avoid. Bare patterns; the SQL OR-combines them
# and `~*` makes the match case-insensitive on the Postgres side. The
# IGNORECASE flag in `re.compile` on the in-memory side mirrors that.
# Override via ``KB_TITLE_QUALITY_PATTERNS`` (comma-separated) if a
# new framing word lands in production.
DEFAULT_PATTERNS: tuple[str, ...] = (
    r"^how to ",
    r"^how does ",
    r"^how can ",
    r"^why ",
    r"^what is ",
    r"^when to ",
    r"^on ",
    r"^notes on ",
    r"^introduction to ",
    r"^guide to ",
    r"^overview of ",
    # Trailing question-marks are a strong signal even without the
    # question-word prefix.
    r"\?$",
)


# -------- response model + JSON schema -------------------------------


class TitleProposal(BaseModel):
    """Validated rewriter output. Keep keys aligned with the schema below."""

    title: str = Field(..., min_length=4, max_length=80)
    reason: str | None = None

    def stripped(self) -> str:
        return self.title.strip().strip(".")


_RESPONSE_SCHEMA: dict[str, object] = {
    "name": "knowledge_curator_title_rewrite",
    "schema": {
        "type": "object",
        "additionalProperties": False,
        "required": ["title"],
        "properties": {
            "title": {"type": "string", "minLength": 4, "maxLength": 80},
            "reason": {"type": ["string", "null"]},
        },
    },
    "strict": True,
}


def _system_prompt() -> str:
    # Same title contract the inbox classifier uses, scoped down to
    # the single rewriter task. The model returns ONLY a JSON object.
    return (
        "You rewrite the title of a knowledge-base note. "
        "Output ONLY a JSON object that matches the schema below. "
        "Title contract: 3-8 words, declarative present tense, no "
        'trailing punctuation. Skip framing words like "How to", '
        '"On", "About", "Notes on", "Introduction to" — the title IS '
        'the claim. Good: "Vault Raft unseal requires Shamir keys". '
        'Bad: "How does Vault Raft unseal work?". '
        "Preserve technical proper nouns verbatim (k3s, Helm, "
        "Cloudflare, NixOS, Tailscale). "
        "If the existing title already meets the contract, return it "
        "unchanged. Output schema:\n" + json.dumps(_RESPONSE_SCHEMA["schema"], indent=2)
    )


def _user_prompt(*, title: str, body: str) -> str:
    snippet = body.strip()
    if len(snippet) > 1500:
        snippet = snippet[:1500] + "…"
    return (
        "<existing-title>"
        + title
        + "</existing-title>\n"
        + "<body>\n"
        + snippet
        + "\n</body>\n"
        + "Return the JSON object now."
    )


# -------- HTTP path -------------------------------------------------


class TitleRewriteError(RuntimeError):
    """Raised when the model output is unrecoverably invalid."""


class OllamaTitleRewriter:
    """OpenAI-compatible /v1/chat/completions caller for the
    title-rewrite task.

    Stateless. One instance per pass. The owning maintenance loop
    passes the chat endpoint resolved by ``ollama_router.resolve_chat``
    so the rewrites land on the heavy host when available.
    """

    def __init__(
        self,
        *,
        base_url: str,
        model: str,
        timeout_seconds: float,
        client: httpx.Client | None = None,
    ) -> None:
        self._base_url = base_url.rstrip("/")
        self._model = model
        self._timeout = timeout_seconds
        self._client = client or httpx.Client(timeout=timeout_seconds)
        self._owns_client = client is None

    def close(self) -> None:
        if self._owns_client:
            self._client.close()

    def rewrite(self, *, title: str, body: str) -> TitleProposal:
        payload = {
            "model": self._model,
            "messages": [
                {"role": "system", "content": _system_prompt()},
                {"role": "user", "content": _user_prompt(title=title, body=body)},
            ],
            "temperature": 0.0,
            "response_format": {"type": "json_schema", "json_schema": _RESPONSE_SCHEMA},
            "max_tokens": 256,
        }
        url = self._base_url + "/chat/completions"
        try:
            response = self._client.post(url, json=payload)
            response.raise_for_status()
            data = response.json()
        except (httpx.TimeoutException, httpx.HTTPError) as exc:
            raise TitleRewriteError(
                f"Ollama transport error on {url}: {type(exc).__name__}: {exc}"
            ) from exc
        try:
            raw = str(data["choices"][0]["message"]["content"])
        except (KeyError, IndexError, TypeError) as exc:
            raise TitleRewriteError(
                f"Unexpected /chat/completions response shape: {data!r}"
            ) from exc
        try:
            return TitleProposal.model_validate_json(raw)
        except ValidationError as exc:
            raise TitleRewriteError(f"Title rewrite validation failed: {exc}") from exc


# -------- pass orchestration ----------------------------------------


@dataclass(frozen=True, slots=True)
class TitleQualityStats:
    candidates: int
    rewritten: int
    unchanged: int
    skipped: int


def run_title_quality(
    *,
    store: CuratorStore,
    vault: CuratorVault,
    rewriter: OllamaTitleRewriter,
    vault_clone_dir: Path,
    patterns: list[str],
    batch_size: int,
) -> TitleQualityStats:
    """Single batch — caller invokes once per cron tick.

    For each candidate row:
      1. Ask the model for a new title.
      2. If unchanged or only whitespace-different, skip the writes.
      3. Else: UPDATE kb_notes.title, rewrite the vault file's
         frontmatter, stage + commit the change.
    """

    candidates = store.select_title_quality_batch(patterns=patterns, limit=batch_size)
    rewritten = 0
    unchanged = 0
    skipped = 0
    committed_rels: list[str] = []

    for note_id, current_title, body, vault_rel in candidates:
        try:
            proposal = rewriter.rewrite(title=current_title, body=body)
        except TitleRewriteError as exc:
            log.warning(
                "title_quality.rewrite_failed",
                note_id=note_id,
                error=str(exc),
            )
            skipped += 1
            continue

        new_title = proposal.stripped()
        if not new_title or new_title.casefold() == current_title.strip().casefold():
            unchanged += 1
            continue

        # Vault file rewrite: keep filename + every frontmatter field
        # except `title` exactly as the curator left it on promote.
        vault_abs = vault_clone_dir / vault_rel
        if not vault_abs.exists():
            log.warning(
                "title_quality.vault_missing",
                note_id=note_id,
                vault_path=vault_rel,
            )
            skipped += 1
            continue
        try:
            existing = parse_note_file(vault_abs)
        except Exception as exc:
            log.warning(
                "title_quality.parse_failed",
                note_id=note_id,
                vault_path=vault_rel,
                error=str(exc),
            )
            skipped += 1
            continue

        # render_note_file rebuilds the markdown from the parsed
        # frontmatter; we only override `new_title`, keeping every
        # other promotion field exactly as the curator left it.
        new_text = render_note_file(
            front=existing,
            new_title=new_title,
            new_scope=existing.scope,
            new_type=existing.type,
            new_tags=existing.tags,
            new_confidence=existing.confidence,
        )
        vault_abs.write_text(new_text, encoding="utf-8")

        rows = store.update_title(note_id=note_id, title=new_title)
        if rows == 0:
            # Row vanished between SELECT and UPDATE — back the
            # vault file out and move on.
            original_text = render_note_file(
                front=existing,
                new_title=existing.title,
                new_scope=existing.scope,
                new_type=existing.type,
                new_tags=existing.tags,
                new_confidence=existing.confidence,
            )
            vault_abs.write_text(original_text, encoding="utf-8")
            log.warning("title_quality.row_gone", note_id=note_id)
            skipped += 1
            continue

        committed_rels.append(vault_rel)
        rewritten += 1
        log.info(
            "title_quality.rewrote",
            note_id=note_id,
            old_title=current_title,
            new_title=new_title,
        )

    if committed_rels:
        vault.commit_paths(
            rels=committed_rels,
            subject=f"curator: title-quality pass touched {len(committed_rels)} notes",
        )

    return TitleQualityStats(
        candidates=len(candidates),
        rewritten=rewritten,
        unchanged=unchanged,
        skipped=skipped,
    )


# -------- entry point -----------------------------------------------


def main() -> int:
    telemetry.configure()
    settings = Settings.from_env(dict(os.environ))

    raw_patterns = os.environ.get("KB_TITLE_QUALITY_PATTERNS", "")
    if raw_patterns.strip():
        patterns = [p.strip() for p in raw_patterns.split(",") if p.strip()]
    else:
        patterns = list(DEFAULT_PATTERNS)

    batch_size = int(os.environ.get("KB_TITLE_BATCH_SIZE", "25"))

    store = PostgresCuratorStore(
        host=settings.db_host,
        port=settings.db_port,
        database=settings.db_name,
        user=settings.db_user,
        password=settings.db_password,
    )
    store.open()

    # Vault clone is mounted RW; we need the same author / ssh-key
    # plumbing as the inbox curator so commits show up under the
    # curator identity and push successfully.
    from git import Actor

    vault = CuratorVault(
        clone_dir=settings.vault_clone_dir,
        author=Actor(settings.curator_author_name, settings.curator_author_email),
        ssh_key_path=settings.vault_ssh_key_path,
        push=True,
    )
    vault.sync()

    chat = resolve_chat(settings)
    log.info(
        "title_quality.chat_endpoint",
        profile=chat.profile,
        model=chat.model,
    )

    try:
        with httpx.Client(timeout=settings.ollama_request_timeout_seconds) as http:
            rewriter = OllamaTitleRewriter(
                base_url=chat.base_url,
                model=chat.model,
                timeout_seconds=settings.ollama_request_timeout_seconds,
                client=http,
            )
            stats = run_title_quality(
                store=store,
                vault=vault,
                rewriter=rewriter,
                vault_clone_dir=settings.vault_clone_dir,
                patterns=patterns,
                batch_size=batch_size,
            )
        log.info(
            "title_quality.complete",
            candidates=stats.candidates,
            rewritten=stats.rewritten,
            unchanged=stats.unchanged,
            skipped=stats.skipped,
            model=chat.model,
            patterns=patterns,
            batch_size=batch_size,
        )
    finally:
        store.close()
    return 0


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    sys.exit(main())
