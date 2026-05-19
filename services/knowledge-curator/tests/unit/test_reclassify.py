"""Unit tests for the topic reclassifier."""

from __future__ import annotations

from collections.abc import Iterable
from contextlib import contextmanager
from datetime import UTC, datetime, timedelta
from typing import Any

from curator.classify import Classification, ClassificationError
from curator.reclassify import ReclassifyOutcome, TopicReclassifier, summarise
from curator.topics import Topic, TopicVocabulary


class _FakeStore:
    """In-memory `kb_notes` + `kb_topics` for the reclassifier."""

    def __init__(
        self,
        *,
        notes: Iterable[tuple[str, str, str, str, str, datetime]],
        vocab_watermark: datetime | None,
    ) -> None:
        # (id, title, body, scope, scope_was_emitted_as_inbox_hint, topic_classified_at)
        self.notes: dict[str, dict[str, Any]] = {
            note_id: {
                "id": note_id,
                "title": title,
                "body": body,
                "scope": scope,
                "topic_classified_at": classified_at,
            }
            for (note_id, title, body, scope, _hint, classified_at) in notes
        }
        self.vocab_watermark = vocab_watermark
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
                if "MAX(created_at) FROM kb_topics" in q:
                    self._fetched = [(store.vocab_watermark,)]
                elif "FROM kb_notes" in q and "topic_classified_at < %s" in q:
                    watermark, limit = params
                    rows = sorted(
                        (n for n in store.notes.values() if n["topic_classified_at"] < watermark),
                        key=lambda n: n["topic_classified_at"],
                    )[:limit]
                    self._fetched = [(n["id"], n["title"], n["body"], n["scope"]) for n in rows]
                elif "UPDATE kb_notes" in q and "SET scope" in q:
                    new_scope, note_id = params
                    note = store.notes.get(note_id)
                    if note is not None:
                        note["scope"] = new_scope
                        note["topic_classified_at"] = datetime.now(UTC)
                    self._fetched = []
                elif "UPDATE kb_notes" in q and "topic_classified_at = NOW()" in q:
                    (note_id,) = params
                    note = store.notes.get(note_id)
                    if note is not None:
                        note["topic_classified_at"] = datetime.now(UTC)
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
        self._store.audit_rows.append(("fake-audit-id", kwargs["actor"], kwargs["action"]))
        return "fake-audit-id"


class _StubClassifier:
    def __init__(self, by_id: dict[str, Classification | ClassificationError]) -> None:
        self._by_id = by_id

    def classify(
        self,
        *,
        title: str,
        body: str,
        neighbours: Any,
        inbox_scope_hint: str,
    ) -> Classification:
        # The reclassifier passes (title, body, []) — the title doubles
        # as the routing key here so each test can script per-note.
        del body, neighbours, inbox_scope_hint
        scripted = self._by_id.get(title)
        if scripted is None:
            raise AssertionError(f"unscripted classify() for title: {title!r}")
        if isinstance(scripted, ClassificationError):
            raise scripted
        return scripted


def _classification(
    *,
    scope: str,
    confidence: float = 0.9,
    topic: str | None = None,
) -> Classification:
    return Classification(
        title="any short title",
        scope=scope,
        topic=topic,
        type="lesson",
        tags=[],
        supersedes=[],
        see_also=[],
        confidence=confidence,
        needs_review_reason=None,
    )


def _vocab(*slugs: str) -> TopicVocabulary:
    return TopicVocabulary([Topic(slug=s) for s in slugs])


def _reclassifier(
    store: _FakeStore, classifier: Any, audit: _FakeAudit, vocab: TopicVocabulary
) -> TopicReclassifier:
    return TopicReclassifier(
        store=store,
        classifier=classifier,
        audit=audit,
        vocabulary=vocab,
    )


# ---- pass-level behaviour ---------------------------------------------------


def test_pass_returns_nothing_when_vocab_watermark_is_null() -> None:
    store = _FakeStore(notes=[], vocab_watermark=None)
    audit = _FakeAudit(store)
    reclassifier = _reclassifier(store, _StubClassifier({}), audit, _vocab())

    assert reclassifier.run_pass() == []
    assert audit.records == []


def test_pass_skips_rows_whose_watermark_is_already_fresh() -> None:
    now = datetime.now(UTC)
    store = _FakeStore(
        notes=[("01A", "title-a", "body", "topic:kotlin", "topic:kotlin", now + timedelta(days=1))],
        vocab_watermark=now,
    )
    audit = _FakeAudit(store)
    reclassifier = _reclassifier(store, _StubClassifier({}), audit, _vocab("kotlin"))

    assert reclassifier.run_pass() == []
    assert audit.records == []


def test_pass_reclassifies_when_scope_changes_and_confidence_is_high() -> None:
    now = datetime.now(UTC)
    store = _FakeStore(
        notes=[
            (
                "01A",
                "title-a",
                "body about hexagonal",
                "topic:software-architecture",
                "topic:software-architecture",
                now - timedelta(days=30),
            )
        ],
        vocab_watermark=now,
    )
    audit = _FakeAudit(store)
    classifier = _StubClassifier(
        {
            "title-a": _classification(
                scope="topic:hexagonal-architecture", topic="hexagonal-architecture", confidence=0.9
            ),
        },
    )
    reclassifier = _reclassifier(
        store,
        classifier,
        audit,
        _vocab("hexagonal-architecture", "software-architecture"),
    )

    outcomes = reclassifier.run_pass()

    assert outcomes == [
        ReclassifyOutcome(
            note_id="01A",
            status="reclassified",
            before_scope="topic:software-architecture",
            after_scope="topic:hexagonal-architecture",
        )
    ]
    assert store.notes["01A"]["scope"] == "topic:hexagonal-architecture"
    assert store.notes["01A"]["topic_classified_at"] > now
    assert len(audit.records) == 1
    assert audit.records[0]["actor"] == "kb-reclassify-topics"
    assert audit.records[0]["action"] == "reclassify_topic"


def test_pass_marks_unchanged_when_classifier_returns_the_same_scope() -> None:
    now = datetime.now(UTC)
    store = _FakeStore(
        notes=[
            (
                "01A",
                "title-a",
                "body",
                "topic:kotlin",
                "topic:kotlin",
                now - timedelta(days=30),
            )
        ],
        vocab_watermark=now,
    )
    audit = _FakeAudit(store)
    classifier = _StubClassifier(
        {"title-a": _classification(scope="topic:kotlin", topic="kotlin")},
    )
    reclassifier = _reclassifier(store, classifier, audit, _vocab("kotlin"))

    outcomes = reclassifier.run_pass()

    assert outcomes[0].status == "unchanged"
    assert store.notes["01A"]["scope"] == "topic:kotlin"
    # Watermark advanced so the row isn't re-considered next pass.
    assert store.notes["01A"]["topic_classified_at"] > now
    assert audit.records == []


def test_pass_skips_under_confidence_floor_but_advances_watermark() -> None:
    now = datetime.now(UTC)
    store = _FakeStore(
        notes=[
            (
                "01A",
                "title-a",
                "body",
                "topic:software-architecture",
                "topic:software-architecture",
                now - timedelta(days=30),
            )
        ],
        vocab_watermark=now,
    )
    audit = _FakeAudit(store)
    classifier = _StubClassifier(
        {
            "title-a": _classification(
                scope="topic:hexagonal-architecture",
                topic="hexagonal-architecture",
                confidence=0.6,
            ),
        },
    )
    reclassifier = _reclassifier(
        store,
        classifier,
        audit,
        _vocab("hexagonal-architecture", "software-architecture"),
    )

    outcomes = reclassifier.run_pass()

    assert outcomes[0].status == "low_confidence"
    assert store.notes["01A"]["scope"] == "topic:software-architecture"
    assert store.notes["01A"]["topic_classified_at"] > now
    assert audit.records == []


def test_pass_rejects_unknown_topic_but_advances_watermark() -> None:
    now = datetime.now(UTC)
    store = _FakeStore(
        notes=[
            (
                "01A",
                "title-a",
                "body",
                "topic:kotlin",
                "topic:kotlin",
                now - timedelta(days=30),
            )
        ],
        vocab_watermark=now,
    )
    audit = _FakeAudit(store)
    classifier = _StubClassifier(
        {"title-a": _classification(scope="topic:made-up", topic="made-up", confidence=0.95)},
    )
    reclassifier = _reclassifier(store, classifier, audit, _vocab("kotlin"))

    outcomes = reclassifier.run_pass()

    assert outcomes[0].status == "invalid_topic"
    assert store.notes["01A"]["scope"] == "topic:kotlin"
    assert store.notes["01A"]["topic_classified_at"] > now
    assert audit.records == []


def test_pass_leaves_row_alone_on_classifier_failure() -> None:
    now = datetime.now(UTC)
    older = now - timedelta(days=30)
    store = _FakeStore(
        notes=[("01A", "title-a", "body", "topic:kotlin", "topic:kotlin", older)],
        vocab_watermark=now,
    )
    audit = _FakeAudit(store)
    classifier = _StubClassifier({"title-a": ClassificationError("connect timeout")})
    reclassifier = _reclassifier(store, classifier, audit, _vocab("kotlin"))

    outcomes = reclassifier.run_pass()

    assert outcomes[0].status == "failed"
    # Watermark unchanged so the next pass retries.
    assert store.notes["01A"]["topic_classified_at"] == older
    assert audit.records == []


def test_pass_respects_max_per_pass_limit() -> None:
    now = datetime.now(UTC)
    base = now - timedelta(days=30)
    notes = [
        (
            f"01{i:02d}",
            f"title-{i}",
            "body",
            "topic:kotlin",
            "topic:kotlin",
            base + timedelta(minutes=i),
        )
        for i in range(10)
    ]
    store = _FakeStore(notes=notes, vocab_watermark=now)
    audit = _FakeAudit(store)
    classifier = _StubClassifier(
        {f"title-{i}": _classification(scope="topic:kotlin", topic="kotlin") for i in range(10)},
    )
    reclassifier = TopicReclassifier(
        store=store,
        classifier=classifier,  # type: ignore[arg-type]
        audit=audit,
        vocabulary=_vocab("kotlin"),
        max_per_pass=4,
    )

    outcomes = reclassifier.run_pass()

    assert len(outcomes) == 4
    # Oldest-first: ids 0..3 picked.
    assert {o.note_id for o in outcomes} == {"0100", "0101", "0102", "0103"}


def test_summarise_counts_outcomes_by_status() -> None:
    outcomes = [
        ReclassifyOutcome(note_id="01A", status="reclassified", before_scope="x", after_scope="y"),
        ReclassifyOutcome(note_id="01B", status="unchanged", before_scope="x", after_scope="x"),
        ReclassifyOutcome(
            note_id="01C", status="low_confidence", before_scope="x", after_scope="x"
        ),
        ReclassifyOutcome(note_id="01D", status="invalid_topic", before_scope="x", after_scope="x"),
        ReclassifyOutcome(note_id="01E", status="failed", before_scope="x", after_scope="x"),
    ]
    assert summarise(outcomes) == {
        "reclassified": 1,
        "unchanged": 1,
        "low_confidence": 1,
        "invalid_topic": 1,
        "failed": 1,
    }
