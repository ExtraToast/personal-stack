"""Audit-row writer for curator-side mutations.

`kb_audit` is owned by knowledge-api (Flyway migration `V4__audit_log.sql`);
the curator is a co-writer when it mutates a note's title, scope, or
tag set. Going via the MCP tool would add a network hop with no
security gain — the curator has Postgres creds via Vault Agent
in-cluster — so this module talks to the table directly.

Schema (mirrored, do not drift):

    kb_audit(
      id          TEXT PRIMARY KEY,         -- ULID, lex-sorts by time
      actor       TEXT NOT NULL,            -- e.g. "kb-renormalise-titles"
      action      TEXT NOT NULL,            -- e.g. "rename_title"
      target_id   TEXT,                     -- kb_notes.id when applicable
      target_kind TEXT,                     -- e.g. "kb_note"
      before_json TEXT,                     -- serialised pre-state snapshot
      after_json  TEXT,                     -- serialised post-state snapshot
      at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
    )

`before_json` / `after_json` are intentionally free-form: each
action shape ships a JSON blob whose keys the consumer parses on
read. That way a new curator pass can add a new `action` without a
Flyway migration.
"""

from __future__ import annotations

import json
from collections.abc import Mapping
from typing import Any, Protocol

from psycopg import sql

from curator.ulid import generate as generate_ulid


class _Store(Protocol):
    def connection(self) -> Any: ...


class AuditRecorder:
    """Single-row writer for `kb_audit`.

    Stateless — one instance per curator pass is fine. The
    connection is acquired per-call from the supplied store's
    pool, which keeps the audit write inside a fresh short-lived
    transaction independent of whatever the caller is doing.
    """

    def __init__(self, *, store: _Store) -> None:
        self._store = store

    def record(
        self,
        *,
        actor: str,
        action: str,
        target_id: str | None = None,
        target_kind: str | None = None,
        before: Mapping[str, Any] | None = None,
        after: Mapping[str, Any] | None = None,
    ) -> str:
        """Insert one audit row. Returns the ULID-shaped audit id."""

        audit_id = generate_ulid()
        before_json = json.dumps(dict(before)) if before is not None else None
        after_json = json.dumps(dict(after)) if after is not None else None
        with self._store.connection() as conn, conn.cursor() as cur:
            cur.execute(
                sql.SQL(
                    "INSERT INTO kb_audit "
                    "(id, actor, action, target_id, target_kind, before_json, after_json) "
                    "VALUES (%s, %s, %s, %s, %s, %s, %s)",
                ),
                (audit_id, actor, action, target_id, target_kind, before_json, after_json),
            )
        return audit_id
