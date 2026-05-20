"""Orchestrator entrypoint — single CronJob tick.

The runner walks every registered :class:`Pass` (an empty list in
this PR; B + C wire the real passes), and for each pass:

    1. Acquires a transaction-scoped advisory lock keyed by the pass
       name. A slow tick that overlaps the next cron trigger blocks
       here instead of racing.
    2. Loads `kb_curator_runs` state.
    3. Asks ``pass.has_work(state)``. If False, records a `no_work`
       skip and moves on. 98% of ticks hit this path on a healthy
       cluster.
    4. Otherwise marks the pass `running`, calls ``pass.run(state)``,
       persists the outcome.

Per-pass exceptions are caught and recorded as `failed` so the next
tick re-tries them; one broken pass never aborts the others.

Run via ``python -m curator.orchestrator``. Designed to exit in <2 s
on a no-work tick so a frequent cron schedule (e.g. */5) does not
amount to meaningful compute when nothing has changed.
"""

from __future__ import annotations

import os
import sys
import time
from collections.abc import Iterable

import structlog

from curator import telemetry
from curator.orchestrator.protocol import Pass
from curator.settings import Settings
from curator.store import PostgresCuratorStore

log = structlog.get_logger(__name__)


def build_passes(
    *,
    settings: Settings,
    store: PostgresCuratorStore,
) -> list[Pass]:
    """Return the passes the orchestrator should run this tick, in
    order.

    PR A intentionally returns an empty list — the scaffolding lands
    safely before any Pass implementation. PR B wires
    :class:`InboxPass` + :class:`NeedsReviewDrainPass`; PR C adds
    :class:`TitleQualityPass` + :class:`RelationEnrichmentPass`.
    """

    return []


def run_tick(
    *,
    store: PostgresCuratorStore,
    passes: Iterable[Pass],
) -> dict[str, int]:
    """One orchestrator tick. Returns a counter dict for caller-side
    metrics / log lines.
    """

    counts = {"no_work": 0, "success": 0, "failed": 0}
    for pass_ in passes:
        try:
            with store.pass_advisory_lock(pass_.name):
                state = store.load_pass_state(pass_.name)
                if not pass_.has_work(state):
                    store.record_no_work(pass_.name)
                    counts["no_work"] += 1
                    log.info(
                        "orchestrator.skip",
                        name=pass_.name,
                        since=state.last_completed_at,
                    )
                    continue
                store.mark_pass_running(pass_.name)
                started = time.monotonic()
                try:
                    outcome = pass_.run(state)
                except Exception as exc:
                    duration = time.monotonic() - started
                    log.exception(
                        "orchestrator.failed",
                        name=pass_.name,
                        duration_seconds=duration,
                    )
                    store.record_pass_outcome(
                        pass_name=pass_.name,
                        status="failed",
                        notes_processed=0,
                        watermark_before=state.watermark,
                        watermark_after=state.watermark,
                        duration_seconds=duration,
                        error=f"{type(exc).__name__}: {exc}",
                    )
                    counts["failed"] += 1
                    continue
                duration = time.monotonic() - started
                store.record_pass_outcome(
                    pass_name=pass_.name,
                    status=outcome.status,
                    notes_processed=outcome.notes_processed,
                    watermark_before=state.watermark,
                    watermark_after=outcome.watermark_after,
                    duration_seconds=duration,
                    error=outcome.error,
                )
                counts[outcome.status] = counts.get(outcome.status, 0) + 1
                log.info(
                    "orchestrator.complete",
                    name=pass_.name,
                    duration_seconds=duration,
                    **outcome.summary(),
                )
        except Exception:
            # Top-level catch keeps one broken pass from blocking the
            # next one. The inner try/except already records failed
            # outcomes; this is the belt for the braces.
            log.exception("orchestrator.lock_or_state_failed", name=pass_.name)
            counts["failed"] += 1
    return counts


def main() -> int:
    telemetry.configure()
    settings = Settings.from_env(dict(os.environ))

    store = PostgresCuratorStore(
        host=settings.db_host,
        port=settings.db_port,
        database=settings.db_name,
        user=settings.db_user,
        password=settings.db_password,
    )
    store.open()
    try:
        passes = build_passes(settings=settings, store=store)
        if not passes:
            log.info("orchestrator.no_passes_registered")
            return 0
        counts = run_tick(store=store, passes=passes)
        log.info("orchestrator.tick_complete", **counts)
    finally:
        store.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
