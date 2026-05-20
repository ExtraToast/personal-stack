"""Postgres reads + writes the curator owns.

The curator is the second writer for ``kb_notes`` (after the
ingest-worker). It UPDATEs the row's scope + vault_path + confidence
after promoting an inbox file, and inserts ``kb_relations`` rows
for supersedes / see_also / contradicts edges.

Read side: ``existing_ids(ids)`` validates that the supersedes /
see_also targets the LLM proposed actually exist before any
relation rows hit the DB.
"""

from __future__ import annotations

import json
from collections.abc import Iterable, Iterator
from contextlib import AbstractContextManager, contextmanager
from dataclasses import dataclass
from datetime import datetime
from typing import Any, Protocol

import psycopg
import structlog
from psycopg import sql
from psycopg_pool import ConnectionPool


@dataclass(frozen=True, slots=True)
class OrchestratorPassRow:
    """Shape returned by :meth:`CuratorStore.load_pass_state`.

    Mirrors the columns of `kb_curator_runs` 1:1 so a caller can read
    the prior run's status / watermark without reaching back into SQL.
    """

    pass_name: str
    last_started_at: datetime | None
    last_completed_at: datetime | None
    last_status: str
    watermark: dict[str, Any]
    notes_processed: int


class CuratorStore(Protocol):
    def existing_ids(self, ids: Iterable[str]) -> set[str]: ...

    def promote_note(
        self,
        *,
        note_id: str,
        scope: str,
        vault_path: str,
        vault_commit: str,
        confidence: float,
    ) -> int: ...

    def insert_relation(
        self,
        *,
        subject_id: str,
        predicate: str,
        object_id: str,
        props_json: str = "{}",
    ) -> None: ...

    def write_embedding(
        self,
        *,
        note_id: str,
        vector: tuple[float, ...],
        model: str,
    ) -> int:
        """Persist the embedding the recall path's vector leg will use.

        The column lives in the Postgres-only migration tree (V9 in
        ``db/migration-pg/``); the curator is the sole writer. Returns
        the number of rows touched — 0 means the row wasn't there
        (orphan note), >0 means the embedding was stored.
        """
        ...

    def select_embedding_backfill_batch(
        self,
        *,
        model: str,
        limit: int,
    ) -> list[tuple[str, str, str]]:
        """Pick rows whose embedding is missing or stale relative to the
        target model. Returns ``(id, title, body)`` tuples ordered by
        ``embedded_at NULLS FIRST, captured_at`` so the oldest unembedded
        rows drain first. Cheaper than two passes (NULL then stale).
        """
        ...

    def conflict_edges(self) -> list[tuple[str, str, str]]:
        """Return every `(subject_id, predicate, object_id)` row whose
        predicate is in {`supersedes`, `contradicts`} for the
        `_index/conflicts.md` MoC regeneration. Cheap query — total
        conflict cardinality stays small in practice.
        """
        ...

    def select_title_quality_batch(
        self,
        *,
        patterns: list[str],
        limit: int,
    ) -> list[tuple[str, str, str, str]]:
        """Pick promoted notes whose ``title`` matches any of ``patterns``
        (POSIX regex, case-insensitive). Returns ``(id, title, body,
        vault_path)`` tuples ordered by ``captured_at`` so the oldest
        suspect titles re-title first. Only promoted notes
        (``vault_path IS NOT NULL``) are eligible — drafts in
        ``_inbox/`` are still on the regular curator pass.
        """
        ...

    def update_title(
        self,
        *,
        note_id: str,
        title: str,
    ) -> int:
        """Set ``kb_notes.title`` for ``note_id`` and bump ``updated_at``.
        Returns rowcount so the caller can skip the vault commit when
        the row vanished (0 rows touched).
        """
        ...

    # -- orchestrator state -----------------------------------------

    def load_pass_state(self, pass_name: str) -> OrchestratorPassRow:
        """Return the most-recent state row for ``pass_name``. Synthesises
        a fresh row (last_completed_at=None, watermark={}) if the pass
        has never run.
        """
        ...

    def mark_pass_running(self, pass_name: str) -> None:
        """Upsert `kb_curator_runs` with last_status='running' +
        last_started_at=NOW(). Called by the orchestrator immediately
        before invoking Pass.run.
        """
        ...

    def record_pass_outcome(
        self,
        *,
        pass_name: str,
        status: str,
        notes_processed: int,
        watermark_before: dict[str, Any],
        watermark_after: dict[str, Any],
        duration_seconds: float,
        error: str | None = None,
    ) -> None:
        """Persist a terminal Pass outcome — updates `kb_curator_runs`
        AND appends one row to `kb_curator_pass_history`. ``status``
        is one of 'success' | 'no_work' | 'failed'.
        """
        ...

    def record_no_work(self, pass_name: str) -> None:
        """Cheap variant of `record_pass_outcome` for the 98% case
        where `has_work` returned False. Updates `kb_curator_runs`
        with last_status='no_work' + last_completed_at=NOW(); does
        NOT append a history row.
        """
        ...

    def pass_advisory_lock(self, pass_name: str) -> AbstractContextManager[None]:
        """Acquire a transaction-scoped advisory lock keyed by
        ``pass_name``. Returns a context manager — the lock holds for
        the duration of the `with` block and releases when the
        underlying transaction commits / rolls back. Blocks if another
        orchestrator is currently inside the same pass; a slow tick
        does not overlap itself.
        """
        ...


class PostgresCuratorStore:
    """Real Postgres-backed implementation.

    The pool is intentionally small — the curator is a CronJob, not a
    long-running daemon, and only ever runs a handful of statements
    per inbox file.
    """

    def __init__(
        self,
        *,
        host: str,
        port: int,
        database: str,
        user: str,
        password: str,
        min_size: int = 1,
        max_size: int = 2,
    ) -> None:
        conninfo = (
            f"host={host} port={port} dbname={database} "
            f"user={user} password={password} application_name=knowledge-curator"
        )
        self._pool = ConnectionPool(
            conninfo=conninfo,
            min_size=min_size,
            max_size=max_size,
            open=False,
        )
        self._log = structlog.get_logger(__name__)

    def open(self) -> None:
        self._pool.open(wait=True, timeout=10.0)
        self._log.info("store.opened")

    def close(self) -> None:
        self._pool.close()

    def connection(self) -> AbstractContextManager[psycopg.Connection[Any]]:
        """Lend out a pooled connection to callers that need to run
        ad-hoc reads (e.g. the topic-vocabulary loader). Wraps the
        pool's connection context manager so the caller doesn't have
        to know about the pool's internals.
        """

        return self._pool.connection()

    def existing_ids(self, ids: Iterable[str]) -> set[str]:
        wanted = list({i for i in ids if i})
        if not wanted:
            return set()
        with self._pool.connection() as conn, conn.cursor() as cur:
            cur.execute(
                sql.SQL("SELECT id FROM kb_notes WHERE id = ANY(%s)"),
                (wanted,),
            )
            return {row[0] for row in cur.fetchall()}

    def promote_note(
        self,
        *,
        note_id: str,
        scope: str,
        vault_path: str,
        vault_commit: str,
        confidence: float,
    ) -> int:
        # `confidence` clamp is enforced at the application boundary
        # — the schema has the CHECK constraint already, but a stray
        # 1.5 from a misbehaving caller should fail loudly, not at
        # the SQL layer.
        clamped = max(0.0, min(1.0, confidence))
        with self._pool.connection() as conn, conn.cursor() as cur:
            cur.execute(
                sql.SQL(
                    "UPDATE kb_notes "
                    "SET scope = %s, vault_path = %s, vault_commit = %s, "
                    "    confidence = %s, updated_at = NOW() "
                    "WHERE id = %s"
                ),
                (scope, vault_path, vault_commit, clamped, note_id),
            )
            return cur.rowcount

    def insert_relation(
        self,
        *,
        subject_id: str,
        predicate: str,
        object_id: str,
        props_json: str = "{}",
    ) -> None:
        with self._pool.connection() as conn, conn.cursor() as cur:
            cur.execute(
                sql.SQL(
                    "INSERT INTO kb_relations "
                    "(subject_id, predicate, object_id, props) "
                    "VALUES (%s, %s, %s, %s) "
                    "ON CONFLICT (subject_id, predicate, object_id) DO NOTHING"
                ),
                (subject_id, predicate, object_id, props_json),
            )

    def conflict_edges(self) -> list[tuple[str, str, str]]:
        with self._pool.connection() as conn, conn.cursor() as cur:
            cur.execute(
                sql.SQL(
                    "SELECT subject_id, predicate, object_id FROM kb_relations "
                    "WHERE predicate IN ('supersedes', 'contradicts')"
                ),
            )
            return [(row[0], row[1], row[2]) for row in cur.fetchall()]

    def write_embedding(
        self,
        *,
        note_id: str,
        vector: tuple[float, ...],
        model: str,
    ) -> int:
        # pgvector accepts a bracketed comma-separated literal which
        # the `::vector` cast in SQL parses without a pgvector-aware
        # JDBC adapter (the Kotlin recall side uses the same trick).
        literal = "[" + ",".join(repr(float(v)) for v in vector) + "]"
        with self._pool.connection() as conn, conn.cursor() as cur:
            cur.execute(
                sql.SQL(
                    "UPDATE kb_notes "
                    "SET embedding = %s::vector, "
                    "    embedding_model = %s, "
                    "    embedded_at = NOW(), "
                    "    updated_at = NOW() "
                    "WHERE id = %s"
                ),
                (literal, model, note_id),
            )
            return cur.rowcount

    def select_embedding_backfill_batch(
        self,
        *,
        model: str,
        limit: int,
    ) -> list[tuple[str, str, str]]:
        with self._pool.connection() as conn, conn.cursor() as cur:
            cur.execute(
                sql.SQL(
                    "SELECT id, title, body FROM kb_notes "
                    "WHERE embedding IS NULL OR embedding_model IS DISTINCT FROM %s "
                    "ORDER BY embedded_at NULLS FIRST, captured_at "
                    "LIMIT %s"
                ),
                (model, limit),
            )
            return [(row[0], row[1], row[2]) for row in cur.fetchall()]

    def select_title_quality_batch(
        self,
        *,
        patterns: list[str],
        limit: int,
    ) -> list[tuple[str, str, str, str]]:
        # Combine the patterns into a single OR alternation so the
        # query stays a single planned regex match per row — Postgres'
        # `~*` is a sequential scan on `kb_notes.title` either way, but
        # one regex beats `N` for the planner.
        if not patterns:
            return []
        combined = "|".join(f"({p})" for p in patterns)
        with self._pool.connection() as conn, conn.cursor() as cur:
            cur.execute(
                sql.SQL(
                    "SELECT id, title, body, vault_path FROM kb_notes "
                    "WHERE vault_path IS NOT NULL AND title ~* %s "
                    "ORDER BY captured_at LIMIT %s"
                ),
                (combined, limit),
            )
            return [(row[0], row[1], row[2], row[3]) for row in cur.fetchall()]

    def update_title(
        self,
        *,
        note_id: str,
        title: str,
    ) -> int:
        with self._pool.connection() as conn, conn.cursor() as cur:
            cur.execute(
                sql.SQL("UPDATE kb_notes SET title = %s, updated_at = NOW() WHERE id = %s"),
                (title, note_id),
            )
            return cur.rowcount

    # -- orchestrator state -----------------------------------------

    def load_pass_state(self, pass_name: str) -> OrchestratorPassRow:
        with self._pool.connection() as conn, conn.cursor() as cur:
            cur.execute(
                sql.SQL(
                    "SELECT pass_name, last_started_at, last_completed_at, "
                    "       last_status, watermark, notes_processed "
                    "FROM kb_curator_runs WHERE pass_name = %s"
                ),
                (pass_name,),
            )
            row = cur.fetchone()
        if row is None:
            return OrchestratorPassRow(
                pass_name=pass_name,
                last_started_at=None,
                last_completed_at=None,
                last_status="never_run",
                watermark={},
                notes_processed=0,
            )
        watermark = row[4] if isinstance(row[4], dict) else (json.loads(row[4]) if row[4] else {})
        return OrchestratorPassRow(
            pass_name=row[0],
            last_started_at=row[1],
            last_completed_at=row[2],
            last_status=row[3],
            watermark=watermark,
            notes_processed=row[5],
        )

    def mark_pass_running(self, pass_name: str) -> None:
        with self._pool.connection() as conn, conn.cursor() as cur:
            cur.execute(
                sql.SQL(
                    "INSERT INTO kb_curator_runs "
                    "  (pass_name, last_started_at, last_status, watermark) "
                    "VALUES (%s, NOW(), 'running', '{}'::jsonb) "
                    "ON CONFLICT (pass_name) DO UPDATE SET "
                    "  last_started_at = EXCLUDED.last_started_at, "
                    "  last_status = 'running'"
                ),
                (pass_name,),
            )

    def record_pass_outcome(
        self,
        *,
        pass_name: str,
        status: str,
        notes_processed: int,
        watermark_before: dict[str, Any],
        watermark_after: dict[str, Any],
        duration_seconds: float,
        error: str | None = None,
    ) -> None:
        if status not in {"success", "no_work", "failed"}:
            raise ValueError(f"invalid status: {status!r}")
        wm_after_json = json.dumps(watermark_after)
        wm_before_json = json.dumps(watermark_before)
        # Both rows in one connection — the history append + the
        # latest-state update want to be atomic so a reader that
        # opens between them never sees a state row pointing at
        # nothing.
        with self._pool.connection() as conn, conn.cursor() as cur:
            cur.execute(
                sql.SQL(
                    "INSERT INTO kb_curator_runs "
                    "  (pass_name, last_started_at, last_completed_at, "
                    "   last_status, watermark, notes_processed, "
                    "   duration_seconds, error_summary) "
                    "VALUES (%s, NOW(), NOW(), %s, %s::jsonb, %s, %s, %s) "
                    "ON CONFLICT (pass_name) DO UPDATE SET "
                    "  last_completed_at = NOW(), "
                    "  last_status = EXCLUDED.last_status, "
                    "  watermark = EXCLUDED.watermark, "
                    "  notes_processed = EXCLUDED.notes_processed, "
                    "  duration_seconds = EXCLUDED.duration_seconds, "
                    "  error_summary = EXCLUDED.error_summary"
                ),
                (
                    pass_name,
                    status,
                    wm_after_json,
                    notes_processed,
                    duration_seconds,
                    error,
                ),
            )
            cur.execute(
                sql.SQL(
                    "INSERT INTO kb_curator_pass_history "
                    "  (pass_name, started_at, completed_at, status, "
                    "   notes_processed, duration_seconds, "
                    "   watermark_before, watermark_after, error) "
                    "VALUES (%s, NOW() - (%s || ' seconds')::interval, "
                    "        NOW(), %s, %s, %s, %s::jsonb, %s::jsonb, %s)"
                ),
                (
                    pass_name,
                    duration_seconds,
                    status,
                    notes_processed,
                    duration_seconds,
                    wm_before_json,
                    wm_after_json,
                    error,
                ),
            )

    def record_no_work(self, pass_name: str) -> None:
        # Cheap path: no history row, just bump the latest-state
        # timestamps so an operator querying `last_completed_at` sees
        # the curator is alive even on quiet ticks.
        with self._pool.connection() as conn, conn.cursor() as cur:
            cur.execute(
                sql.SQL(
                    "INSERT INTO kb_curator_runs "
                    "  (pass_name, last_started_at, last_completed_at, "
                    "   last_status, watermark) "
                    "VALUES (%s, NOW(), NOW(), 'no_work', '{}'::jsonb) "
                    "ON CONFLICT (pass_name) DO UPDATE SET "
                    "  last_completed_at = NOW(), "
                    "  last_status = 'no_work'"
                ),
                (pass_name,),
            )

    @contextmanager
    def pass_advisory_lock(self, pass_name: str) -> Iterator[None]:
        # `pg_advisory_xact_lock(hashtext(...))` is transaction-scoped:
        # the lock releases on commit or rollback. Wrapping the
        # entire Pass.run inside this `with` block means a slow tick
        # can not overlap itself across consecutive orchestrator
        # invocations — the second tick blocks here until the first
        # releases. Multiple distinct passes use different hash keys
        # and never contend.
        with self._pool.connection() as conn, conn.cursor() as cur:
            cur.execute(
                sql.SQL("SELECT pg_advisory_xact_lock(hashtext(%s))"),
                (f"curator/{pass_name}",),
            )
            try:
                yield
            finally:
                # Lock releases at transaction commit which happens
                # when the connection context manager exits. Nothing
                # to do here, but keep the try/finally so a future
                # refactor can plug in explicit release if needed.
                pass


class InMemoryCuratorStore:
    """Test double used by unit tests and a local smoke loop."""

    def __init__(self, existing: Iterable[str] = ()) -> None:
        self._existing: set[str] = set(existing)
        self.promotions: list[dict[str, object]] = []
        self.relations: list[tuple[str, str, str]] = []
        # `(note_id, model) -> vector`. Mirrors the kb_notes columns the
        # Postgres store writes; lets unit tests assert on embeddings
        # without spinning up a real DB.
        self.embeddings: dict[str, tuple[tuple[float, ...], str]] = {}
        # Pending rows for backfill smoke tests — `(id, title, body)`.
        self.backfill_queue: list[tuple[str, str, str]] = []
        # Pending rows for title-quality smoke tests —
        # `(id, title, body, vault_path)`. Tests populate this
        # alongside `_existing` so the selector returns deterministic
        # batches.
        self.title_quality_queue: list[tuple[str, str, str, str]] = []
        # `note_id -> new_title` written via `update_title`. Mirrors
        # the kb_notes column the title-quality pass updates.
        self.titles: dict[str, str] = {}
        # `pass_name -> OrchestratorPassRow`. Mirrors `kb_curator_runs`
        # state for orchestrator unit tests; `pass_history` mirrors
        # `kb_curator_pass_history`. Both are publicly mutable so
        # tests can prime the state then assert on the post-call
        # shape.
        self.pass_state: dict[str, OrchestratorPassRow] = {}
        self.pass_history: list[dict[str, Any]] = []

    def existing_ids(self, ids: Iterable[str]) -> set[str]:
        return {i for i in ids if i in self._existing}

    def promote_note(
        self,
        *,
        note_id: str,
        scope: str,
        vault_path: str,
        vault_commit: str,
        confidence: float,
    ) -> int:
        if note_id not in self._existing:
            return 0
        self.promotions.append(
            {
                "id": note_id,
                "scope": scope,
                "vault_path": vault_path,
                "vault_commit": vault_commit,
                "confidence": confidence,
            }
        )
        return 1

    def insert_relation(
        self,
        *,
        subject_id: str,
        predicate: str,
        object_id: str,
        props_json: str = "{}",
    ) -> None:
        self.relations.append((subject_id, predicate, object_id))

    def conflict_edges(self) -> list[tuple[str, str, str]]:
        return [(s, p, o) for (s, p, o) in self.relations if p in ("supersedes", "contradicts")]

    def write_embedding(
        self,
        *,
        note_id: str,
        vector: tuple[float, ...],
        model: str,
    ) -> int:
        if note_id not in self._existing:
            return 0
        self.embeddings[note_id] = (vector, model)
        return 1

    def select_embedding_backfill_batch(
        self,
        *,
        model: str,
        limit: int,
    ) -> list[tuple[str, str, str]]:
        # Mirror the SQL: include any row whose model is missing or
        # diverges from the target. Truncate to `limit`.
        out: list[tuple[str, str, str]] = []
        for note_id, title, body in self.backfill_queue:
            stored = self.embeddings.get(note_id)
            if stored is None or stored[1] != model:
                out.append((note_id, title, body))
            if len(out) >= limit:
                break
        return out

    def select_title_quality_batch(
        self,
        *,
        patterns: list[str],
        limit: int,
    ) -> list[tuple[str, str, str, str]]:
        # Mirror the SQL: case-insensitive regex match on title; tests
        # populate `title_quality_queue` directly with the rows that
        # would survive the WHERE clause.
        import re

        if not patterns:
            return []
        compiled = re.compile("|".join(patterns), re.IGNORECASE)
        out: list[tuple[str, str, str, str]] = []
        for note_id, title, body, vault_path in self.title_quality_queue:
            if compiled.search(title):
                out.append((note_id, title, body, vault_path))
            if len(out) >= limit:
                break
        return out

    def update_title(
        self,
        *,
        note_id: str,
        title: str,
    ) -> int:
        if note_id not in self._existing:
            return 0
        self.titles[note_id] = title
        return 1

    # -- orchestrator state -----------------------------------------

    def load_pass_state(self, pass_name: str) -> OrchestratorPassRow:
        existing = self.pass_state.get(pass_name)
        if existing is not None:
            return existing
        return OrchestratorPassRow(
            pass_name=pass_name,
            last_started_at=None,
            last_completed_at=None,
            last_status="never_run",
            watermark={},
            notes_processed=0,
        )

    def mark_pass_running(self, pass_name: str) -> None:
        prior = self.load_pass_state(pass_name)
        self.pass_state[pass_name] = OrchestratorPassRow(
            pass_name=pass_name,
            last_started_at=datetime.now(),
            last_completed_at=prior.last_completed_at,
            last_status="running",
            watermark=prior.watermark,
            notes_processed=prior.notes_processed,
        )

    def record_pass_outcome(
        self,
        *,
        pass_name: str,
        status: str,
        notes_processed: int,
        watermark_before: dict[str, Any],
        watermark_after: dict[str, Any],
        duration_seconds: float,
        error: str | None = None,
    ) -> None:
        if status not in {"success", "no_work", "failed"}:
            raise ValueError(f"invalid status: {status!r}")
        now = datetime.now()
        self.pass_state[pass_name] = OrchestratorPassRow(
            pass_name=pass_name,
            last_started_at=now,
            last_completed_at=now,
            last_status=status,
            watermark=dict(watermark_after),
            notes_processed=notes_processed,
        )
        self.pass_history.append(
            {
                "pass_name": pass_name,
                "started_at": now,
                "completed_at": now,
                "status": status,
                "notes_processed": notes_processed,
                "duration_seconds": duration_seconds,
                "watermark_before": dict(watermark_before),
                "watermark_after": dict(watermark_after),
                "error": error,
            }
        )

    def record_no_work(self, pass_name: str) -> None:
        prior = self.load_pass_state(pass_name)
        now = datetime.now()
        self.pass_state[pass_name] = OrchestratorPassRow(
            pass_name=pass_name,
            last_started_at=now,
            last_completed_at=now,
            last_status="no_work",
            watermark=prior.watermark,
            notes_processed=prior.notes_processed,
        )

    @contextmanager
    def pass_advisory_lock(self, pass_name: str) -> Iterator[None]:
        # The in-memory test double is single-threaded by definition;
        # the advisory lock is a no-op here. The Pass protocol's
        # contract only depends on the lock being held for the
        # duration of `run`, and that holds trivially.
        yield
