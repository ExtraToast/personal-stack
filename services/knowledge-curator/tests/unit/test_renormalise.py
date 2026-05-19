"""Unit tests for the curator's retroactive title renormaliser.

The renormaliser uses the same `OllamaClassifier` as the inbox
pass — these tests stub it so they don't depend on a live Ollama.
The DB layer is also stubbed via a tiny in-process fake.
"""

from __future__ import annotations

from collections.abc import Iterable
from contextlib import contextmanager
from typing import Any

from curator.classify import Classification, ClassificationError
from curator.renormalise import RenameOutcome, TitleRenormaliser, _meets_contract, summarise

# ---- structural contract -----------------------------------------------------


def test_meets_contract_accepts_a_short_declarative_claim() -> None:
    assert _meets_contract("Hexagonal architecture trades complexity for testability", 80)
    assert _meets_contract("Vault Raft unseal requires Shamir keys", 80)


def test_meets_contract_rejects_long_titles() -> None:
    over_budget = "A" * 81
    assert not _meets_contract(over_budget, 80)


def test_meets_contract_rejects_trailing_punctuation() -> None:
    assert not _meets_contract("Vault unseal is hard.", 80)
    assert not _meets_contract("How does forward-auth work?", 80)
    assert not _meets_contract("Wow this works!", 80)


def test_meets_contract_rejects_too_short() -> None:
    assert not _meets_contract("Vue", 80)


def test_meets_contract_rejects_lowercase_first_char() -> None:
    assert not _meets_contract("hexagonal architecture is great", 80)


# ---- in-process fakes for the DB + classifier -------------------------------


class _FakeStore:
    """Two-table in-memory store. `notes` mirrors the columns the
    renormaliser reads / updates on `kb_notes`; the cursor delegates
    to handlers based on the SQL prefix.
    """

    def __init__(self, notes: Iterable[tuple[str, str, str, str, bool]]) -> None:
        # (id, title, body, scope, title_locked)
        self.notes: dict[str, dict[str, Any]] = {
            note_id: {
                "id": note_id,
                "title": title,
                "body": body,
                "scope": scope,
                "title_locked": locked,
            }
            for (note_id, title, body, scope, locked) in notes
        }
        self.audit_rows: list[tuple[Any, ...]] = []

    @contextmanager
    def connection(self) -> Any:
        store = self

        class _Cur:
            def __enter__(self) -> _Cur:
                return self

            def __exit__(self, *_: object) -> None:
                return None

            def execute(self, query: object, params: tuple[Any, ...] = ()) -> None:
                q = str(query)
                if "FROM kb_notes" in q and "char_length(title)" in q:
                    budget, limit = params
                    rows = sorted(
                        (
                            n
                            for n in store.notes.values()
                            if not n["title_locked"] and len(n["title"]) > budget
                        ),
                        key=lambda n: n["id"],
                        reverse=True,
                    )[:limit]
                    self._fetched = [(n["id"], n["title"], n["body"], n["scope"]) for n in rows]
                elif "UPDATE kb_notes" in q and "SET title" in q:
                    new_title, note_id = params
                    note = store.notes.get(note_id)
                    if note is not None and not note["title_locked"]:
                        note["title"] = new_title
                    self._fetched = []
                elif "INSERT INTO kb_audit" in q:
                    store.audit_rows.append(params)
                    self._fetched = []
                else:
                    self._fetched = []

            def fetchall(self) -> list[tuple[Any, ...]]:
                return list(self._fetched)

        class _Conn:
            def __enter__(self) -> _Conn:
                return self

            def __exit__(self, *_: object) -> None:
                return None

            def cursor(self) -> _Cur:
                return _Cur()

        yield _Conn()


class _FakeAudit:
    def __init__(self, store: _FakeStore) -> None:
        self._store = store
        self.records: list[dict[str, Any]] = []

    def record(self, **kwargs: Any) -> str:
        self.records.append(kwargs)
        self._store.audit_rows.append(
            (
                "fake-audit-id",
                kwargs["actor"],
                kwargs["action"],
                kwargs.get("target_id"),
                kwargs.get("target_kind"),
                str(kwargs.get("before") or ""),
                str(kwargs.get("after") or ""),
            ),
        )
        return "fake-audit-id"


class _StubClassifier:
    """Returns a scripted classification per candidate title. An
    unmapped call raises so a forgotten stub fails noisily.
    """

    def __init__(self, by_title: dict[str, Classification | ClassificationError]) -> None:
        self._by_title = by_title

    def classify(
        self,
        *,
        title: str,
        body: str,
        neighbours: Any,
        inbox_scope_hint: str,
    ) -> Classification:
        del body, neighbours, inbox_scope_hint
        scripted = self._by_title.get(title)
        if scripted is None:
            raise AssertionError(f"unscripted classify() for title: {title!r}")
        if isinstance(scripted, ClassificationError):
            raise scripted
        return scripted


def _classification(title: str) -> Classification:
    return Classification(
        title=title,
        scope="topic:kotlin",
        topic="kotlin",
        type="lesson",
        tags=[],
        supersedes=[],
        see_also=[],
        confidence=0.8,
        needs_review_reason=None,
    )


def _unvalidated_classification(title: str) -> Classification:
    """Build a Classification that bypasses Pydantic validation.

    The renormaliser's own contract check is a belt-and-braces
    layer after Pydantic — these tests exercise that layer for
    title shapes Pydantic would otherwise reject. In production
    the path fires when the classifier emits a title that's
    within the Pydantic schema but still violates the prose
    contract (e.g. a short title with trailing punctuation).
    """

    return Classification.model_construct(
        title=title,
        scope="topic:kotlin",
        topic="kotlin",
        type="lesson",
        tags=[],
        supersedes=[],
        see_also=[],
        confidence=0.8,
        needs_review_reason=None,
    )


def _renormaliser(store: _FakeStore, classifier: Any, audit: _FakeAudit) -> TitleRenormaliser:
    return TitleRenormaliser(
        store=store,
        classifier=classifier,
        audit=audit,
    )


def _long(prefix: str) -> str:
    """Build a deterministic > 80-char title. Tests need to be sure
    the renormaliser SELECT picks the row up.
    """

    return prefix + "x" * (90 - len(prefix))


# ---- run_pass shape ----------------------------------------------------------


def test_pass_skips_rows_already_within_the_budget() -> None:
    store = _FakeStore(notes=[("01A", "Short fine title", "body", "topic:kotlin", False)])
    audit = _FakeAudit(store)
    renormaliser = _renormaliser(store, _StubClassifier({}), audit)

    outcomes = renormaliser.run_pass()

    assert outcomes == []
    assert audit.records == []


def test_pass_renames_a_long_title_with_a_qualifying_replacement() -> None:
    long_title = _long("Discussion of hexagonal architecture ")
    new_title = "Hexagonal architecture trades complexity for testability"
    store = _FakeStore(notes=[("01A", long_title, "body about hex", "topic:kotlin", False)])
    audit = _FakeAudit(store)
    classifier = _StubClassifier({long_title: _classification(new_title)})
    renormaliser = _renormaliser(store, classifier, audit)

    outcomes = renormaliser.run_pass()

    assert outcomes == [
        RenameOutcome(
            note_id="01A",
            status="renamed",
            before_title=long_title,
            after_title=new_title,
        )
    ]
    assert store.notes["01A"]["title"] == new_title
    assert len(audit.records) == 1
    assert audit.records[0]["actor"] == "kb-renormalise-titles"
    assert audit.records[0]["action"] == "rename_title"
    assert audit.records[0]["target_id"] == "01A"
    assert audit.records[0]["before"] == {"title": long_title}
    assert audit.records[0]["after"] == {"title": new_title}


def test_pass_skips_when_replacement_has_trailing_punctuation() -> None:
    long_title = _long("Another overly long title ")
    punctuated_replacement = "How does forward-auth actually work?"
    store = _FakeStore(notes=[("01A", long_title, "body", "topic:kotlin", False)])
    audit = _FakeAudit(store)
    classifier = _StubClassifier({long_title: _classification(punctuated_replacement)})
    renormaliser = _renormaliser(store, classifier, audit)

    outcomes = renormaliser.run_pass()

    assert outcomes[0].status == "skipped"
    assert store.notes["01A"]["title"] == long_title
    assert audit.records == []


def test_pass_skips_when_replacement_still_exceeds_the_budget() -> None:
    # Pydantic blocks > 80-char titles, but the renormaliser's own
    # contract check is the safety net for a downgraded schema or
    # bypassed validation. Build a Classification via model_construct
    # to exercise that branch.
    long_title = _long("Yet another overly long title ")
    over_budget_replacement = "A" * 90
    store = _FakeStore(notes=[("01A", long_title, "body", "topic:kotlin", False)])
    audit = _FakeAudit(store)
    classifier = _StubClassifier(
        {long_title: _unvalidated_classification(over_budget_replacement)},
    )
    renormaliser = _renormaliser(store, classifier, audit)

    outcomes = renormaliser.run_pass()

    assert outcomes[0].status == "skipped"
    assert store.notes["01A"]["title"] == long_title
    assert audit.records == []


def test_pass_records_kept_when_classifier_returns_the_same_title() -> None:
    # Same defensive branch — model_construct bypasses the > 80-char
    # block so the renormaliser sees the same long title back.
    long_title = _long("Idempotent overly long title ")
    store = _FakeStore(notes=[("01A", long_title, "body", "topic:kotlin", False)])
    audit = _FakeAudit(store)
    classifier = _StubClassifier(
        {long_title: _unvalidated_classification(long_title)},
    )
    renormaliser = _renormaliser(store, classifier, audit)

    outcomes = renormaliser.run_pass()

    assert outcomes[0].status == "kept"
    assert store.notes["01A"]["title"] == long_title
    assert audit.records == []


def test_pass_records_failure_when_classifier_raises() -> None:
    long_title = _long("Classifier-failure title ")
    store = _FakeStore(notes=[("01A", long_title, "body", "topic:kotlin", False)])
    audit = _FakeAudit(store)
    classifier = _StubClassifier({long_title: ClassificationError("connect timeout")})
    renormaliser = _renormaliser(store, classifier, audit)

    outcomes = renormaliser.run_pass()

    assert outcomes[0].status == "failed"
    assert "classify-failed" in outcomes[0].reason
    assert store.notes["01A"]["title"] == long_title
    assert audit.records == []


def test_pass_does_not_touch_title_locked_rows() -> None:
    long_title = _long("Operator-hand-edited title ")
    store = _FakeStore(notes=[("01A", long_title, "body", "topic:kotlin", True)])
    audit = _FakeAudit(store)
    renormaliser = _renormaliser(store, _StubClassifier({}), audit)

    outcomes = renormaliser.run_pass()

    assert outcomes == []
    assert store.notes["01A"]["title"] == long_title
    assert audit.records == []


def test_pass_respects_max_per_pass_limit() -> None:
    notes = [
        (f"01{i:02d}", _long(f"Long title {i} "), "b", "topic:kotlin", False) for i in range(5)
    ]
    store = _FakeStore(notes=notes)
    audit = _FakeAudit(store)
    classifier = _StubClassifier(
        {original: _classification("Crisp rewritten title") for (_, original, *_) in notes},
    )
    renormaliser = TitleRenormaliser(
        store=store,
        classifier=classifier,  # type: ignore[arg-type]
        audit=audit,
        max_per_pass=3,
    )

    outcomes = renormaliser.run_pass()

    assert len(outcomes) == 3
    assert all(o.status == "renamed" for o in outcomes)


def test_summarise_counts_outcomes_by_status() -> None:
    outcomes = [
        RenameOutcome(note_id="01A", status="renamed", before_title="x", after_title="y"),
        RenameOutcome(note_id="01B", status="renamed", before_title="x", after_title="y"),
        RenameOutcome(note_id="01C", status="kept", before_title="x", after_title="x"),
        RenameOutcome(note_id="01D", status="skipped", before_title="x", after_title="x"),
        RenameOutcome(note_id="01E", status="failed", before_title="x", after_title="x"),
    ]
    assert summarise(outcomes) == {"renamed": 2, "kept": 1, "skipped": 1, "failed": 1}
