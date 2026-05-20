"""Unit tests for the curator embedding backfill batch.

Covers:
  - The watermark query: rows are pulled when their embedding is
    missing OR the model diverges.
  - The per-row soft-failure path: an embedder exception logs +
    skips the row, the batch continues.
  - The empty queue: no candidates → zero embedded, zero skipped.
"""

from __future__ import annotations

from dataclasses import dataclass

from curator.backfill_embeddings import run_backfill
from curator.embed import Embedding
from curator.store import InMemoryCuratorStore


@dataclass
class _StubEmbedder:
    model: str
    calls: list[str]
    fail_on: set[str]

    def embed(self, text: str) -> Embedding:
        self.calls.append(text)
        if any(token in text for token in self.fail_on):
            raise RuntimeError("ollama unavailable")
        return Embedding(vector=(0.1, 0.2, 0.3), model=self.model)


def _seeded_store(rows: list[tuple[str, str, str]]) -> InMemoryCuratorStore:
    store = InMemoryCuratorStore(existing=[row[0] for row in rows])
    store.backfill_queue = list(rows)
    return store


def test_backfill_drains_unembedded_rows_and_records_model() -> None:
    rows = [
        ("01A", "title-A", "body-A"),
        ("01B", "title-B", "body-B"),
    ]
    store = _seeded_store(rows)
    embedder = _StubEmbedder(model="qwen3-embedding:0.6b", calls=[], fail_on=set())

    stats = run_backfill(store=store, embedder=embedder, batch_size=10)

    assert stats.candidates == 2
    assert stats.embedded == 2
    assert stats.skipped == 0
    assert store.embeddings["01A"] == ((0.1, 0.2, 0.3), "qwen3-embedding:0.6b")
    assert store.embeddings["01B"] == ((0.1, 0.2, 0.3), "qwen3-embedding:0.6b")


def test_backfill_skips_rows_when_embedder_raises_but_continues_batch() -> None:
    rows = [
        ("01A", "title-A", "good body"),
        ("01B", "title-B", "POISON body"),
        ("01C", "title-C", "also good"),
    ]
    store = _seeded_store(rows)
    embedder = _StubEmbedder(model="qwen3-embedding:0.6b", calls=[], fail_on={"POISON"})

    stats = run_backfill(store=store, embedder=embedder, batch_size=10)

    assert stats.candidates == 3
    assert stats.embedded == 2
    assert stats.skipped == 1
    assert "01A" in store.embeddings
    assert "01B" not in store.embeddings
    assert "01C" in store.embeddings


def test_backfill_respects_model_watermark_and_re_embeds_stale_rows() -> None:
    rows = [("01A", "title-A", "body-A")]
    store = _seeded_store(rows)
    # Row already has an embedding from a previous model — the
    # backfill must re-embed because the watermark moved.
    store.embeddings["01A"] = ((0.0, 0.0, 0.0), "nomic-embed-text")
    embedder = _StubEmbedder(model="qwen3-embedding:0.6b", calls=[], fail_on=set())

    stats = run_backfill(store=store, embedder=embedder, batch_size=10)

    assert stats.candidates == 1
    assert stats.embedded == 1
    assert store.embeddings["01A"] == ((0.1, 0.2, 0.3), "qwen3-embedding:0.6b")


def test_backfill_empty_queue_is_noop() -> None:
    store = _seeded_store([])
    embedder = _StubEmbedder(model="qwen3-embedding:0.6b", calls=[], fail_on=set())

    stats = run_backfill(store=store, embedder=embedder, batch_size=10)

    assert stats.candidates == 0
    assert stats.embedded == 0
    assert stats.skipped == 0
    assert embedder.calls == []
