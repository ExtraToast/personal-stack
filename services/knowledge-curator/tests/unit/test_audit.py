"""Unit tests for the curator-side audit-row writer."""

from __future__ import annotations

import json
from contextlib import contextmanager
from typing import Any

from curator.audit import AuditRecorder


class _FakeStore:
    def __init__(self) -> None:
        self.executed: list[tuple[str, tuple[Any, ...]]] = []

    @contextmanager
    def connection(self) -> Any:
        store = self

        class _Cur:
            def __enter__(self) -> _Cur:
                return self

            def __exit__(self, *_: object) -> None:
                return None

            def execute(self, query: object, params: tuple[Any, ...]) -> None:
                store.executed.append((str(query), params))

        class _Conn:
            def __enter__(self) -> _Conn:
                return self

            def __exit__(self, *_: object) -> None:
                return None

            def cursor(self) -> _Cur:
                return _Cur()

        yield _Conn()


def test_record_inserts_one_row_with_ulid_shaped_id() -> None:
    store = _FakeStore()
    audit = AuditRecorder(store=store)

    audit_id = audit.record(
        actor="kb-renormalise-titles",
        action="rename_title",
        target_id="01HABC",
        target_kind="kb_note",
        before={"title": "old"},
        after={"title": "new"},
    )

    assert len(audit_id) == 26
    assert len(store.executed) == 1
    query, params = store.executed[0]
    assert "INSERT INTO kb_audit" in query
    assert params[0] == audit_id
    assert params[1] == "kb-renormalise-titles"
    assert params[2] == "rename_title"
    assert params[3] == "01HABC"
    assert params[4] == "kb_note"
    assert json.loads(params[5]) == {"title": "old"}
    assert json.loads(params[6]) == {"title": "new"}


def test_record_serialises_missing_before_after_as_nulls() -> None:
    store = _FakeStore()
    audit = AuditRecorder(store=store)

    audit.record(actor="manual-fixup", action="annotate", target_id=None, target_kind=None)

    _, params = store.executed[0]
    assert params[3] is None
    assert params[4] is None
    assert params[5] is None
    assert params[6] is None
