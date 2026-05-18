from __future__ import annotations

from curator.store import InMemoryCuratorStore, PostgresCuratorStore


def test_in_memory_store_filters_existing_ids() -> None:
    store = InMemoryCuratorStore(existing=["A", "B"])
    assert store.existing_ids(["A", "C"]) == {"A"}
    assert store.existing_ids([]) == set()


def test_in_memory_store_promote_returns_zero_when_row_missing() -> None:
    store = InMemoryCuratorStore(existing=[])
    rows = store.promote_note(
        note_id="MISSING",
        scope="topic:vault",
        vault_path="topics/vault/lesson/x.md",
        vault_commit="abc",
        confidence=0.9,
    )
    assert rows == 0
    assert store.promotions == []


def test_in_memory_store_promote_records_when_row_exists() -> None:
    store = InMemoryCuratorStore(existing=["01H"])
    rows = store.promote_note(
        note_id="01H",
        scope="topic:vault",
        vault_path="topics/vault/lesson/x.md",
        vault_commit="abc",
        confidence=0.9,
    )
    assert rows == 1
    assert store.promotions[0]["scope"] == "topic:vault"


def test_in_memory_store_records_relations() -> None:
    store = InMemoryCuratorStore()
    store.insert_relation(subject_id="A", predicate="supersedes", object_id="B")
    assert store.relations == [("A", "supersedes", "B")]


def test_postgres_store_constructs_pool_without_opening() -> None:
    # Same pattern the worker tests use: the pool stays quiescent so
    # we can instantiate the store in CI without a live Postgres.
    store = PostgresCuratorStore(
        host="pg",
        port=5432,
        database="kb",
        user="u",
        password="p",
        min_size=1,
        max_size=2,
    )
    assert store is not None
