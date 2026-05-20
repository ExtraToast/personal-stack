"""Embedding backfill for kb_notes rows whose embedding is missing or
stale relative to the target model.

Designed as a single-shot batch — the CronJob invokes
``python -m curator.backfill_embeddings`` once per schedule. Each
invocation pulls a bounded batch (``KB_BACKFILL_BATCH_SIZE``, default
100), embeds via the same Ollama model the curator promote-path uses,
and UPDATEs the row. The job exits 0 when the batch is processed
(success or partial), non-zero only on configuration errors. Per-row
failures are logged + audited and DO NOT abort the batch — the next
pass picks them up.

Watermark: a row's ``embedding_model`` column. A swap to a different
model surfaces every previously-embedded row as a candidate, so the
queue drains in well-defined passes.
"""

from __future__ import annotations

import os
import sys
from dataclasses import dataclass

import httpx
import structlog

from curator import telemetry
from curator.embed import OllamaEmbedder
from curator.settings import Settings
from curator.store import CuratorStore, PostgresCuratorStore

log = structlog.get_logger(__name__)


@dataclass(frozen=True, slots=True)
class BackfillStats:
    candidates: int
    embedded: int
    skipped: int


def run_backfill(
    *,
    store: CuratorStore,
    embedder: OllamaEmbedder,
    batch_size: int,
) -> BackfillStats:
    """Single batch — caller invokes once per cron tick."""
    candidates = store.select_embedding_backfill_batch(
        model=embedder.model,
        limit=batch_size,
    )
    embedded = 0
    skipped = 0
    for note_id, title, body in candidates:
        try:
            embedding = embedder.embed(f"{title}\n\n{body}")
        except Exception as exc:
            log.warning(
                "backfill.embed_failed",
                note_id=note_id,
                model=embedder.model,
                error=str(exc),
            )
            skipped += 1
            continue
        rows = store.write_embedding(
            note_id=note_id,
            vector=embedding.vector,
            model=embedder.model,
        )
        if rows == 0:
            # Row vanished between SELECT and UPDATE (rare — only the
            # curator writes; would imply a manual DELETE). Treat as
            # skip, not fatal.
            log.warning("backfill.row_gone", note_id=note_id)
            skipped += 1
            continue
        embedded += 1
    return BackfillStats(candidates=len(candidates), embedded=embedded, skipped=skipped)


def main() -> int:
    telemetry.configure()
    settings = Settings.from_env(dict(os.environ))
    batch_size = int(os.environ.get("KB_BACKFILL_BATCH_SIZE", "100"))

    store = PostgresCuratorStore(
        host=settings.db_host,
        port=settings.db_port,
        database=settings.db_name,
        user=settings.db_user,
        password=settings.db_password,
    )
    store.open()
    try:
        with httpx.Client(timeout=settings.ollama_request_timeout_seconds) as http:
            embedder = OllamaEmbedder(
                base_url=settings.ollama_base_url,
                model=settings.ollama_embedding_model,
                timeout_seconds=settings.ollama_request_timeout_seconds,
                client=http,
            )
            stats = run_backfill(
                store=store,
                embedder=embedder,
                batch_size=batch_size,
            )
        log.info(
            "backfill.complete",
            candidates=stats.candidates,
            embedded=stats.embedded,
            skipped=stats.skipped,
            model=settings.ollama_embedding_model,
            batch_size=batch_size,
        )
    finally:
        store.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
