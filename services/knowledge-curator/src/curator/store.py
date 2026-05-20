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

from collections.abc import Iterable
from contextlib import AbstractContextManager
from typing import Any, Protocol

import psycopg
import structlog
from psycopg import sql
from psycopg_pool import ConnectionPool


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
