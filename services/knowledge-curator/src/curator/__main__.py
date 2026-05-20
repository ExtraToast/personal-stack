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

import os
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
from curator.lightrag import LightRagClient
from curator.ollama_router import resolve_chat
from curator.projects import ProjectVocabulary
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

    store = PostgresCuratorStore(
        host=settings.db_host,
        port=settings.db_port,
        database=settings.db_name,
        user=settings.db_user,
        password=settings.db_password,
    )
    store.open()

    # Topic vocabulary moves from a static ConfigMap to the kb_topics
    # table in V2 of the schema. Load from DB first; fall back to YAML
    # when the table is empty so a deploy that has not yet run the V3
    # seed migration still classifies. Once the table is populated in
    # production the YAML fallback is dead code and the next PR drops
    # the topics_yaml_path setting entirely.
    topics = _load_topics(store, settings, log)
    projects = _load_projects(store, log)

    shared_http = httpx.Client(timeout=settings.ollama_request_timeout_seconds)
    # Resolve the chat endpoint ONCE per pass: probes the heavy URL
    # (rx7900xtx host-native Ollama) and falls back to the in-cluster
    # CPU Ollama if the probe times out. Embedding calls stay on the
    # light endpoint regardless — flipping embedding models per pass
    # would force the backfill CronJob into permanent re-embedding.
    chat_endpoint = resolve_chat(settings)
    log.info(
        "curator.chat_endpoint",
        profile=chat_endpoint.profile,
        model=chat_endpoint.model,
    )
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
        # tokens. When it isn't, the Vault Agent template renders
        # an empty string, the recall calls go out as
        # `Authorization: Bearer ` (empty), and the API returns
        # 401. The relation resolver swallows that as a generic
        # recall failure and falls back to dropping dead edges —
        # so the symptom is silent unless someone reads the warn
        # logs. Surface it loudly on boot.
        log.warning(
            "curator.bearer_empty",
            hint=(
                "KNOWLEDGE_API_BEARER_TOKEN is empty. Recall calls "
                "will return 401 and the relation resolver will "
                "fall back to dropping dead edges. Add a `curator` "
                "field to secret/knowledge-system/mcp-bearer."
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
    vault.sync()

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
        # Wires the recall path's pgvector ANN leg — every promoted
        # note now lands with an embedding alongside its vault commit.
        # Soft-failure on the curator side: an Ollama outage leaves
        # the row's embedding NULL, the backfill CronJob picks it up.
        embedder=embedder,
        embedding_model=settings.ollama_embedding_model,
    )

    inbox_root = settings.vault_clone_dir / "_inbox"
    candidates = _list_inbox(inbox_root)
    # Opt-in backfill: when the env is set, also walk the existing
    # `_inbox/_needs-review/` tree and re-run each file through the
    # pipeline. The relation resolver now treats most relation
    # mismatches as soft drops rather than hard rejections, so notes
    # previously rejected for `relation-target-missing` typically
    # promote on the second pass. Single-pass — the operator unsets
    # the env when the queue is drained.
    if os.environ.get("CURATOR_DRAIN_NEEDS_REVIEW", "").lower() in {"1", "true", "yes"}:
        review_root = inbox_root / "_needs-review"
        review_candidates = _list_needs_review(review_root)
        log.info("curator.drain_needs_review", candidates=len(review_candidates))
        candidates = candidates + review_candidates
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


def _load_topics(
    store: PostgresCuratorStore,
    settings: Settings,
    log: structlog.BoundLogger,
) -> TopicVocabulary:
    """Prefer the DB-backed vocabulary; fall back to the bundled YAML
    when the table is empty (pre-V3-seed deploys) or unreachable.

    A YAML fallback that *also* raises would crash the curator on
    every cycle until the seed runs; a silent empty vocabulary would
    let every capture route to `_inbox/_needs-review/`. Logging the
    chosen source keeps the operator honest about which path is live.
    """

    try:
        with store.connection() as conn:
            db_topics = TopicVocabulary.from_db(conn)
        if db_topics.slugs:
            log.info(
                "curator.topics_loaded",
                source="kb_topics",
                count=len(db_topics.slugs),
            )
            return db_topics
        log.warning("curator.topics_db_empty", path=str(settings.topics_yaml_path))
    except Exception as exc:  # pragma: no cover — falls through to YAML
        log.warning(
            "curator.topics_db_unreachable",
            error=str(exc),
            fallback=str(settings.topics_yaml_path),
        )
    yaml_topics = TopicVocabulary.from_yaml(settings.topics_yaml_path)
    log.info(
        "curator.topics_loaded",
        source="yaml",
        count=len(yaml_topics.slugs),
    )
    return yaml_topics


def _load_projects(
    store: PostgresCuratorStore,
    log: structlog.BoundLogger,
) -> ProjectVocabulary:
    """Prefer the DB-backed project vocabulary; fall back to an empty
    one when the table is empty or unreachable.

    Unlike topics, projects do not have a YAML fallback in the
    image (the seed migration is the source of truth). An empty
    vocabulary routes every `project:<...>` emission to needs-
    review with reason `unknown-project-slug:<emitted>` — the
    correct posture for a pre-V8-seed deploy, since we'd rather
    flag the row than silently scatter it into a hallucinated
    `projects/<bogus>/` folder.
    """

    try:
        with store.connection() as conn:
            db_projects = ProjectVocabulary.from_db(conn)
        if db_projects.slugs:
            log.info(
                "curator.projects_loaded",
                source="kb_projects",
                count=len(db_projects.slugs),
            )
            return db_projects
        log.warning("curator.projects_db_empty")
    except Exception as exc:  # pragma: no cover — accept the empty vocab
        log.warning("curator.projects_db_unreachable", error=str(exc))
    return ProjectVocabulary([])


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


def _list_needs_review(review_root: Path) -> list[str]:
    """Walk every `.md` file directly under `_inbox/_needs-review/`.
    Used only by the opt-in `CURATOR_DRAIN_NEEDS_REVIEW` backfill —
    the regular pass skips the directory via [_list_inbox]'s prefix
    filter. Returns paths relative to the vault root so the promoter
    sees the same shape it gets for fresh inbox files.
    """

    if not review_root.exists():
        return []
    out: list[str] = []
    for path in sorted(review_root.glob("*.md")):
        rel = path.relative_to(review_root.parent.parent).as_posix()
        out.append(rel)
    return out


if __name__ == "__main__":  # pragma: no cover — k8s CronJob entry
    sys.exit(main())
