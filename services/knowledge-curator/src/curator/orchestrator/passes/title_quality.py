"""Title-quality pass — wraps the existing
:func:`curator.maintenance.title_quality.run_title_quality` batch
function in the orchestrator Pass protocol.

The pass replaces the standalone ``knowledge-curator-title-quality``
CronJob (PR #412) — same code path under the hood, but gated on
``has_work(state)`` so the orchestrator only pays the LLM cost when
new candidates exist OR the chat model itself changed.

Watermark shape::

    {"model": "qwen3:32b", "patterns_hash": "<sha256 of joined patterns>"}

When either field drifts between ticks, every promoted note matching
the patterns becomes a candidate again — that's the auto-sweep
behaviour the user asked for ("we don't need to re-run unless things
have changed").
"""

from __future__ import annotations

import hashlib
from pathlib import Path

import httpx
import structlog

from curator.maintenance.title_quality import (
    DEFAULT_PATTERNS,
    OllamaTitleRewriter,
    run_title_quality,
)
from curator.orchestrator.protocol import PassOutcome, PassState
from curator.store import CuratorStore
from curator.vault import CuratorVault

log = structlog.get_logger(__name__)


def _patterns_hash(patterns: list[str]) -> str:
    payload = "\n".join(patterns).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()[:16]


class TitleQualityPass:
    """Re-title promoted notes whose title hits a framing-word regex.

    Name: ``'title_quality'``.
    """

    name = "title_quality"

    def __init__(
        self,
        *,
        store: CuratorStore,
        vault: CuratorVault,
        vault_clone_dir: Path,
        http_client: httpx.Client,
        chat_base_url: str,
        chat_model: str,
        chat_timeout_seconds: float,
        patterns: list[str] | None = None,
        batch_size: int = 25,
    ) -> None:
        self._store = store
        self._vault = vault
        self._vault_clone_dir = vault_clone_dir
        self._http = http_client
        self._chat_base_url = chat_base_url
        self._chat_model = chat_model
        self._chat_timeout = chat_timeout_seconds
        self._patterns = list(patterns) if patterns is not None else list(DEFAULT_PATTERNS)
        self._batch_size = batch_size
        self._patterns_hash = _patterns_hash(self._patterns)

    def has_work(self, state: PassState) -> bool:
        # Model drift OR patterns drift = full sweep. Cheap to detect.
        wm_model = state.watermark.get("model")
        wm_hash = state.watermark.get("patterns_hash")
        if wm_model != self._chat_model or wm_hash != self._patterns_hash:
            log.info(
                "title_quality.config_drift",
                wm_model=wm_model,
                cur_model=self._chat_model,
                wm_patterns=wm_hash,
                cur_patterns=self._patterns_hash,
            )
            return self._has_any_candidate()
        # Same config as last run — only re-evaluate if a new candidate
        # showed up since then.
        return self._has_any_candidate()

    def _has_any_candidate(self) -> bool:
        # The cheapest probe we can run with the existing store
        # surface: select up to 1 row. SQL hits the regex index on
        # `kb_notes.title` and bails on the first match. Avoids
        # adding a dedicated COUNT(*) endpoint while staying O(1).
        return bool(self._store.select_title_quality_batch(patterns=self._patterns, limit=1))

    def run(self, state: PassState) -> PassOutcome:
        rewriter = OllamaTitleRewriter(
            base_url=self._chat_base_url,
            model=self._chat_model,
            timeout_seconds=self._chat_timeout,
            client=self._http,
        )
        stats = run_title_quality(
            store=self._store,
            vault=self._vault,
            rewriter=rewriter,
            vault_clone_dir=self._vault_clone_dir,
            patterns=self._patterns,
            batch_size=self._batch_size,
        )
        log.info(
            "title_quality.complete",
            candidates=stats.candidates,
            rewritten=stats.rewritten,
            unchanged=stats.unchanged,
            skipped=stats.skipped,
            model=self._chat_model,
        )
        return PassOutcome(
            status="success" if stats.candidates > 0 else "no_work",
            notes_processed=stats.rewritten,
            watermark_after={
                "model": self._chat_model,
                "patterns_hash": self._patterns_hash,
                "last_candidates": stats.candidates,
                "last_rewritten": stats.rewritten,
            },
        )
