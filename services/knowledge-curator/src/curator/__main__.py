"""Entry point: one curator pass.

Run via `python -m curator` from a k8s CronJob. Each invocation:

1. Wires up the four collaborators (classifier, recall, store, vault).
2. Pulls --rebase to absorb concurrent writes.
3. Walks `_inbox/<YYYY-MM-DD>/*.md` (skipping `_inbox/_needs-review/`).
4. Promotes each file through `Promoter.promote_inbox_file`.
5. Logs a per-file outcome line; exit code 0 even when individual
   files fail — the next pass retries them.
"""

from __future__ import annotations

import sys
from pathlib import Path

import httpx
import structlog
from git import Actor

from curator.classify import OllamaClassifier
from curator.embed import OllamaEmbedder
from curator.indexes import (
    ConflictEdge,
    load_promoted_notes,
    write_conflicts,
    write_recent,
    write_topic_mocs,
)
from curator.promote import Promoter
from curator.recall import RecallClient
from curator.settings import Settings
from curator.store import PostgresCuratorStore
from curator.telemetry import configure as configure_telemetry
from curator.topics import TopicVocabulary
from curator.vault import CuratorVault


def main() -> int:
    settings = Settings.from_env()
    configure_telemetry(level=settings.log_level, service_version=settings.service_version)
    log = structlog.get_logger(__name__)

    topics = TopicVocabulary.from_yaml(settings.topics_yaml_path)
    log.info("curator.topics_loaded", count=len(topics.slugs), slugs=list(topics.slugs))

    shared_http = httpx.Client(timeout=settings.ollama_request_timeout_seconds)
    classifier = OllamaClassifier(
        base_url=settings.ollama_base_url,
        model=settings.ollama_chat_model,
        topic_slugs=topics.slugs,
        timeout_seconds=settings.ollama_request_timeout_seconds,
        client=shared_http,
    )
    embedder = OllamaEmbedder(
        base_url=settings.ollama_base_url,
        model=settings.ollama_embedding_model,
        timeout_seconds=settings.ollama_request_timeout_seconds,
        client=shared_http,
    )
    recall = RecallClient(
        base_url=settings.knowledge_api_base_url,
        bearer_token=settings.knowledge_api_bearer_token,
        timeout_seconds=15.0,
    )
    store = PostgresCuratorStore(
        host=settings.db_host,
        port=settings.db_port,
        database=settings.db_name,
        user=settings.db_user,
        password=settings.db_password,
    )
    store.open()
    vault = CuratorVault(
        clone_dir=settings.vault_clone_dir,
        author=Actor(settings.curator_author_name, settings.curator_author_email),
        ssh_key_path=settings.vault_ssh_key_path,
        push=True,
    )
    vault.sync()

    promoter = Promoter(
        classifier=classifier,
        recall=recall,
        store=store,
        vault=vault,
        topics=topics,
        clone_dir=settings.vault_clone_dir,
        confidence_floor=settings.classify_confidence_floor,
        recall_limit=settings.classify_top_k_neighbours,
    )

    inbox_root = settings.vault_clone_dir / "_inbox"
    candidates = _list_inbox(inbox_root)
    log.info("curator.pass_start", candidates=len(candidates))

    promoted_count = 0
    for rel in candidates:
        outcome = promoter.promote_inbox_file(rel)
        log.info(
            "curator.outcome",
            note_id=outcome.note_id,
            status=outcome.status,
            destination=outcome.destination_rel,
            reason=outcome.reason,
        )
        if outcome.status == "promoted":
            promoted_count += 1
        # Reserve the embedder reference until the pgvector ANN leg
        # consumes it — silences the unused-import warning without
        # pulling the module out of the wiring (PR 3 lands the call
        # path).
        _ = embedder

    if promoted_count > 0:
        _regenerate_indexes(settings.vault_clone_dir, store, vault, log)

    return 0


def _regenerate_indexes(
    clone_dir: Path,
    store: PostgresCuratorStore,
    vault: CuratorVault,
    log: structlog.BoundLogger,
) -> None:
    """Refresh `_index/recent.md`, the per-topic MoCs, and
    `_index/conflicts.md` after a successful promote-pass.

    Each file is regenerated from disk + DB state so hand edits in
    `_index/` are intentionally overwritten. The `.gitattributes`
    `merge=union` rule on `_index/**` means concurrent regenerations
    from a future scale-out converge rather than fight.
    """

    notes = load_promoted_notes(clone_dir)
    edges = [ConflictEdge(s, p, o) for (s, p, o) in store.conflict_edges()]
    paths = [
        write_recent(clone_dir, notes),
        *write_topic_mocs(clone_dir, notes),
        write_conflicts(clone_dir, edges, notes),
    ]
    rels = [p.relative_to(clone_dir).as_posix() for p in paths]
    vault.commit_paths(
        rels=rels,
        subject=f"curator(index): regenerate {len(rels)} index file(s)",
    )
    log.info("curator.indexes_regenerated", files=len(rels))


def _list_inbox(inbox_root: Path) -> list[str]:
    if not inbox_root.exists():
        return []
    out: list[str] = []
    for path in sorted(inbox_root.rglob("*.md")):
        rel = path.relative_to(inbox_root.parent).as_posix()
        # Skip files that already sit under `_inbox/_needs-review/` —
        # those are the curator's own previous "I gave up" markers
        # and require a human edit before the next pass picks them up.
        if rel.startswith("_inbox/_needs-review/"):
            continue
        out.append(rel)
    return out


if __name__ == "__main__":  # pragma: no cover — k8s CronJob entry
    sys.exit(main())
