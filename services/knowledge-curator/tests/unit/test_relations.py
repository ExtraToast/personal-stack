"""Unit tests for the relation resolver — Plan C of the curator
quality plan.

We stub `RecallClient` + `CuratorStore` with hand-rolled fakes so
the tests run instantly + offline. The classifier's emitted shape
is what feeds the resolver — we don't need a live Ollama here.
"""

from __future__ import annotations

from collections.abc import Iterable

from curator.recall import RecallHit
from curator.relations import RelationResolver


class _FakeStore:
    def __init__(self, existing: Iterable[str]) -> None:
        self._existing = set(existing)

    def existing_ids(self, ids: Iterable[str]) -> set[str]:
        wanted = list({i for i in ids if i})
        return {i for i in wanted if i in self._existing}


class _FakeRecall:
    """Returns whatever the test scripted per query string. Tracks
    the queries it received so a test can assert "we never called
    recall for the ULID-shaped one"."""

    def __init__(self, scripted: dict[str, list[RecallHit]] | None = None) -> None:
        self._scripted = scripted or {}
        self.queries: list[str] = []

    def recall(self, *, query: str, limit: int = 5, scope: str | None = None) -> list[RecallHit]:
        del limit, scope
        self.queries.append(query)
        return self._scripted.get(query, [])


def _hit(rid: str, score: float = 0.9) -> RecallHit:
    return RecallHit(id=rid, type="lesson", scope="personal", title="t", snippet="s", score=score)


# ---- shape: nothing changes when every target already exists ----


def test_resolver_passes_through_when_every_target_exists() -> None:
    store = _FakeStore(existing={"01ABC", "01DEF", "01GHI"})
    recall = _FakeRecall()
    resolver = RelationResolver(recall=recall, store=store)

    out = resolver.resolve(supersedes=["01ABC"], see_also=["01DEF", "01GHI"])

    assert out.supersedes == ("01ABC",)
    assert out.see_also == ("01DEF", "01GHI")
    assert not out.has_changes
    assert recall.queries == []  # never called when nothing missing


def test_resolver_short_circuits_on_empty_input() -> None:
    resolver = RelationResolver(recall=_FakeRecall(), store=_FakeStore(existing=()))
    out = resolver.resolve(supersedes=[], see_also=[])
    assert out == out.__class__(supersedes=(), see_also=(), substituted=(), dropped=())


# ---- substitution path: title-fragment recall rescues a missing id ----


def test_resolver_substitutes_phrase_shaped_target_via_recall() -> None:
    store = _FakeStore(existing={"01ABC", "01REAL"})
    recall = _FakeRecall(scripted={"hexagonal architecture": [_hit("01REAL", score=0.88)]})
    resolver = RelationResolver(recall=recall, store=store, substitution_score_floor=0.7)

    out = resolver.resolve(supersedes=["01ABC"], see_also=["hexagonal architecture"])

    assert out.supersedes == ("01ABC",)
    assert out.see_also == ("01REAL",)
    assert out.substituted == (("see_also", "hexagonal architecture", "01REAL"),)
    assert out.dropped == ()
    assert recall.queries == ["hexagonal architecture"]


def test_resolver_does_not_substitute_below_score_floor() -> None:
    store = _FakeStore(existing={"01ABC"})
    # Top hit only scores 0.4 — should fall through to drop.
    recall = _FakeRecall(scripted={"some phrase": [_hit("01OTHER", score=0.4)]})
    resolver = RelationResolver(recall=recall, store=store, substitution_score_floor=0.7)

    out = resolver.resolve(supersedes=[], see_also=["some phrase"])

    assert out.see_also == ()
    assert out.dropped == (("see_also", "some phrase"),)
    assert out.substituted == ()


def test_resolver_skips_recall_for_ulid_shaped_misses() -> None:
    # ULID-shaped string (26 chars Crockford base32). Recall against
    # a random id is wasted budget — the resolver should drop
    # straight away.
    store = _FakeStore(existing=set())
    recall = _FakeRecall()
    resolver = RelationResolver(recall=recall, store=store)

    out = resolver.resolve(supersedes=["01HGZX0Z0J0J0J0J0J0J0J0J0J"], see_also=[])

    assert out.supersedes == ()
    assert out.dropped == (("supersedes", "01HGZX0Z0J0J0J0J0J0J0J0J0J"),)
    assert recall.queries == []  # critically — never called


def test_resolver_falls_back_to_drop_when_recall_returns_no_hits() -> None:
    store = _FakeStore(existing=set())
    recall = _FakeRecall(scripted={"unknown thing": []})
    resolver = RelationResolver(recall=recall, store=store)

    out = resolver.resolve(supersedes=["unknown thing"], see_also=[])

    assert out.supersedes == ()
    assert out.dropped == (("supersedes", "unknown thing"),)


def test_resolver_tolerates_recall_raising() -> None:
    class _BoomRecall:
        def recall(self, **_: object) -> list[RecallHit]:
            raise RuntimeError("connect timeout")

    store = _FakeStore(existing=set())
    resolver = RelationResolver(recall=_BoomRecall(), store=store)

    out = resolver.resolve(supersedes=["something"], see_also=[])

    # Failure on recall is treated as "no hits" — drop, do not crash.
    assert out.dropped == (("supersedes", "something"),)


# ---- mixed shape: partial substitution, partial drop, partial keep ----


def test_resolver_partial_outcome_is_recorded_per_predicate() -> None:
    store = _FakeStore(existing={"01KEEP", "01REAL"})
    recall = _FakeRecall(
        scripted={
            "kept by recall": [_hit("01REAL", score=0.9)],
            "lost to recall": [],
        }
    )
    resolver = RelationResolver(recall=recall, store=store, substitution_score_floor=0.7)

    out = resolver.resolve(
        supersedes=["01KEEP", "lost to recall"],
        see_also=["kept by recall"],
    )

    assert out.supersedes == ("01KEEP",)
    assert out.see_also == ("01REAL",)
    assert out.substituted == (("see_also", "kept by recall", "01REAL"),)
    assert out.dropped == (("supersedes", "lost to recall"),)


def test_resolver_keeps_predicate_in_audit_tuple() -> None:
    """A single missing id may show up under both supersedes and
    see_also (the classifier sometimes duplicates). The resolver
    should record each occurrence with its predicate so the audit
    log doesn't lose context.
    """

    store = _FakeStore(existing=set())
    recall = _FakeRecall(scripted={})
    resolver = RelationResolver(recall=recall, store=store)

    out = resolver.resolve(supersedes=["same phrase"], see_also=["same phrase"])

    assert out.dropped == (
        ("supersedes", "same phrase"),
        ("see_also", "same phrase"),
    )
