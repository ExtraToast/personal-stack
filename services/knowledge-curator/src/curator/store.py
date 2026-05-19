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
from typing import Protocol

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

    def conflict_edges(self) -> list[tuple[str, str, str]]:
        """Return every `(subject_id, predicate, object_id)` row whose
        predicate is in {`supersedes`, `contradicts`} for the
        `_index/conflicts.md` MoC regeneration. Cheap query — total
        conflict cardinality stays small in practice.
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

    def connection(self):  # noqa: ANN201 — psycopg pool context manager
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


class InMemoryCuratorStore:
    """Test double used by unit tests and a local smoke loop."""

    def __init__(self, existing: Iterable[str] = ()) -> None:
        self._existing: set[str] = set(existing)
        self.promotions: list[dict[str, object]] = []
        self.relations: list[tuple[str, str, str]] = []

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
