"""Orchestrator entrypoint — single CronJob tick.

The runner walks every registered :class:`Pass` and for each pass:

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
from functools import partial
from pathlib import Path

import httpx
import structlog
from git import Actor

from curator import telemetry
from curator.classify import OllamaClassifier
from curator.embed import OllamaEmbedder
from curator.indexes import (
    ConflictEdge,
    load_promoted_notes,
    write_conflicts,
    write_recent,
    write_topic_mocs,
)
from curator.lightrag import LightRagClient
from curator.ollama_router import resolve_chat
from curator.orchestrator.passes import (
    InboxPass,
    NeedsReviewDrainPass,
    RelationEnrichmentPass,
    TitleQualityPass,
)
from curator.orchestrator.protocol import Pass, PassState
from curator.projects import ProjectVocabulary
from curator.promote import Promoter
from curator.recall import RecallClient
from curator.settings import Settings
from curator.store import CuratorStore, PostgresCuratorStore
from curator.topics import TopicVocabulary
from curator.vault import CuratorVault

log = structlog.get_logger(__name__)


def build_passes(
    *,
    settings: Settings,
    store: PostgresCuratorStore,
) -> list[Pass]:
    """Construct + wire the registered passes.

    Two passes today: :class:`InboxPass` (promote fresh captures from
    ``_inbox/<day>/``) and :class:`NeedsReviewDrainPass` (re-classify
    files stuck in ``_inbox/_needs-review/``). Both share the heavy
    collaborators (`Promoter`, `CuratorVault`, the heavy/light chat
    endpoint resolver, etc.) — constructed once per tick.

    Order matters: inbox runs before drain so a fresh capture that
    fails validation in this tick lands in `_needs-review/` and gets
    a second chance on the same tick's drain.
    """

    topics = _load_topics(store, settings)
    projects = _load_projects(store)

    chat_endpoint = resolve_chat(settings)
    log.info(
        "orchestrator.chat_endpoint",
        profile=chat_endpoint.profile,
        model=chat_endpoint.model,
    )

    shared_http = httpx.Client(timeout=settings.ollama_request_timeout_seconds)
    classifier = OllamaClassifier(
        base_url=chat_endpoint.base_url,
        model=chat_endpoint.model,
        topic_slugs=topics.slugs,
        project_slugs=projects.slugs,
        timeout_seconds=settings.ollama_request_timeout_seconds,
        client=shared_http,
    )
    embedder = OllamaEmbedder(
        base_url=settings.ollama_base_url,
        model=settings.ollama_embedding_model,
        timeout_seconds=settings.ollama_request_timeout_seconds,
        client=shared_http,
    )
    if not settings.knowledge_api_bearer_token:
        # Missing-field-in-Vault is the typical cause: the
        # `curator` field on `secret/knowledge-system/mcp-bearer`
        # has to be patched in by hand alongside the per-device
        # tokens. When it isn't, recall calls go out as
        # `Authorization: Bearer ` (empty) and the API returns 401.
        log.warning(
            "orchestrator.bearer_empty",
            hint=(
                "KNOWLEDGE_API_BEARER_TOKEN is empty. Recall calls "
                "will return 401 and the relation resolver will "
                "fall back to dropping dead edges."
            ),
        )
    recall = RecallClient(
        base_url=settings.knowledge_api_base_url,
        bearer_token=settings.knowledge_api_bearer_token,
        timeout_seconds=15.0,
    )
    vault = CuratorVault(
        clone_dir=settings.vault_clone_dir,
        author=Actor(settings.curator_author_name, settings.curator_author_email),
        ssh_key_path=settings.vault_ssh_key_path,
        push=True,
    )
    # NB: vault.sync() is intentionally NOT called here — each pass
    # runs sync() inside its own `run()`, scoped to the work it's
    # actually about to do. A no-work tick should not touch git.

    lightrag = LightRagClient(
        base_url=settings.lightrag_base_url,
        timeout_seconds=settings.lightrag_request_timeout_seconds,
        enabled=bool(settings.lightrag_base_url),
    )

    promoter = Promoter(
        classifier=classifier,
        recall=recall,
        store=store,
        vault=vault,
        topics=topics,
        projects=projects,
        clone_dir=settings.vault_clone_dir,
        confidence_floor=settings.classify_confidence_floor,
        recall_limit=settings.classify_top_k_neighbours,
        lightrag=lightrag,
        embedder=embedder,
        embedding_model=settings.ollama_embedding_model,
    )

    regenerate = partial(
        _regenerate_indexes,
        clone_dir=settings.vault_clone_dir,
        store=store,
        vault=vault,
    )

    return [
        InboxPass(
            vault_clone_dir=settings.vault_clone_dir,
            promoter=promoter,
            vault=vault,
            store=store,
            regenerate_indexes=regenerate,
        ),
        NeedsReviewDrainPass(
            vault_clone_dir=settings.vault_clone_dir,
            promoter=promoter,
            vault=vault,
        ),
        # Title-quality + relation-enrichment run AFTER inbox + drain
        # so a freshly-promoted note in this very tick is eligible for
        # title polish + see_also discovery on the NEXT tick rather
        # than the same one. Keeps each pass's view of the data
        # stable for the duration of its run.
        TitleQualityPass(
            store=store,
            vault=vault,
            vault_clone_dir=settings.vault_clone_dir,
            http_client=shared_http,
            chat_base_url=chat_endpoint.base_url,
            chat_model=chat_endpoint.model,
            chat_timeout_seconds=settings.ollama_request_timeout_seconds,
        ),
        RelationEnrichmentPass(
            store=store,
            recall=recall,
            topics=topics,
            projects=projects,
            http_client=shared_http,
            chat_base_url=chat_endpoint.base_url,
            chat_model=chat_endpoint.model,
            chat_timeout_seconds=settings.ollama_request_timeout_seconds,
        ),
    ]


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
                row = store.load_pass_state(pass_.name)
                state = PassState(
                    pass_name=row.pass_name,
                    last_started_at=row.last_started_at,
                    last_completed_at=row.last_completed_at,
                    last_status=row.last_status,
                    watermark=row.watermark,
                    notes_processed=row.notes_processed,
                )
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


# -------- support / helpers ----------------------------------------


def _load_topics(store: PostgresCuratorStore, settings: Settings) -> TopicVocabulary:
    try:
        with store.connection() as conn:
            db_topics = TopicVocabulary.from_db(conn)
        if db_topics.slugs:
            log.info("orchestrator.topics_loaded", source="kb_topics", count=len(db_topics.slugs))
            return db_topics
        log.warning("orchestrator.topics_db_empty", path=str(settings.topics_yaml_path))
    except Exception as exc:  # pragma: no cover — falls through to YAML
        log.warning(
            "orchestrator.topics_db_unreachable",
            error=str(exc),
            fallback=str(settings.topics_yaml_path),
        )
    yaml_topics = TopicVocabulary.from_yaml(settings.topics_yaml_path)
    log.info("orchestrator.topics_loaded", source="yaml", count=len(yaml_topics.slugs))
    return yaml_topics


def _load_projects(store: PostgresCuratorStore) -> ProjectVocabulary:
    try:
        with store.connection() as conn:
            db_projects = ProjectVocabulary.from_db(conn)
        if db_projects.slugs:
            log.info(
                "orchestrator.projects_loaded",
                source="kb_projects",
                count=len(db_projects.slugs),
            )
            return db_projects
        log.warning("orchestrator.projects_db_empty")
    except Exception as exc:  # pragma: no cover — accept the empty vocab
        log.warning("orchestrator.projects_db_unreachable", error=str(exc))
    return ProjectVocabulary([])


def _regenerate_indexes(
    *,
    clone_dir: Path,
    store: CuratorStore,
    vault: CuratorVault,
) -> None:
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
    log.info("orchestrator.indexes_regenerated", files=len(rels))


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
        counts = run_tick(store=store, passes=passes)
        log.info("orchestrator.tick_complete", **counts)
    finally:
        store.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
