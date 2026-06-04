"""Unit tests for TitleQualityPass + RelationEnrichmentPass."""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

import httpx
import respx
from git import Actor, Repo

from curator.orchestrator.passes.relation_enrichment import RelationEnrichmentPass
from curator.orchestrator.passes.tag_reclassification import TagReclassificationPass
from curator.orchestrator.passes.title_quality import TitleQualityPass, _patterns_hash
from curator.orchestrator.protocol import PassState
from curator.projects import Project, ProjectVocabulary
from curator.recall import RecallHit
from curator.reclassify import TagReclassifyOutcome
from curator.store import InMemoryCuratorStore
from curator.topics import Topic, TopicVocabulary
from curator.vault import CuratorVault


def _state(name: str, *, watermark: dict | None = None) -> PassState:
    return PassState(
        pass_name=name,
        last_started_at=None,
        last_completed_at=None,
        last_status="never_run",
        watermark=watermark or {},
        notes_processed=0,
    )


def _make_vault(tmp_path: Path) -> CuratorVault:
    clone_dir = tmp_path / "vault"
    clone_dir.mkdir()
    repo = Repo.init(clone_dir)
    (clone_dir / ".gitkeep").write_text("")
    repo.index.add([".gitkeep"])
    repo.index.commit(
        "init",
        author=Actor("seed", "seed@example.test"),
        committer=Actor("seed", "seed@example.test"),
    )
    return CuratorVault(
        clone_dir=clone_dir,
        author=Actor("curator", "curator@knowledge.test"),
        ssh_key_path="",
        push=False,
    )


def _topics() -> TopicVocabulary:
    return TopicVocabulary([Topic(slug="test")])


def _projects() -> ProjectVocabulary:
    return ProjectVocabulary([Project(slug="ignored")])


@dataclass
class _FakeTagReclassifier:
    watermark: object | None
    outcomes: list[TagReclassifyOutcome]
    has_work_return: bool = True
    has_work_calls: int = 0
    run_watermarks: list[object] | None = None

    def has_work(self) -> bool:
        self.has_work_calls += 1
        return self.has_work_return

    def current_watermark(self) -> object | None:
        return self.watermark

    def run_pass(self, *, watermark: object | None = None) -> list[TagReclassifyOutcome]:
        if self.run_watermarks is None:
            self.run_watermarks = []
        self.run_watermarks.append(watermark)
        return list(self.outcomes)


# -------- TitleQualityPass -----------------------------------------


def test_title_quality_pass_has_work_false_when_no_candidate(tmp_path: Path) -> None:
    store = InMemoryCuratorStore()
    with httpx.Client(timeout=1.0) as http:
        p = TitleQualityPass(
            store=store,
            vault=_make_vault(tmp_path),
            vault_clone_dir=tmp_path / "vault",
            http_client=http,
            chat_base_url="http://x/v1",
            chat_model="qwen3:32b",
            chat_timeout_seconds=1.0,
        )
        assert p.has_work(_state("title_quality")) is False


# -------- TagReclassificationPass ----------------------------------


def test_tag_reclassification_pass_delegates_has_work() -> None:
    reclassifier = _FakeTagReclassifier(watermark=None, outcomes=[], has_work_return=False)
    p = TagReclassificationPass(reclassifier=reclassifier)  # type: ignore[arg-type]

    assert p.has_work(_state("tag_reclassification")) is False
    assert reclassifier.has_work_calls == 1


def test_tag_reclassification_pass_records_watermark_and_counts() -> None:
    watermark = "2026-06-04T10:00:00+00:00"
    outcomes = [
        TagReclassifyOutcome("01A", "retagged", before_tags=("kt",), after_tags=("kotlin",)),
        TagReclassifyOutcome("01B", "unchanged", before_tags=("jvm",), after_tags=("jvm",)),
    ]
    reclassifier = _FakeTagReclassifier(watermark=watermark, outcomes=outcomes)
    p = TagReclassificationPass(reclassifier=reclassifier)  # type: ignore[arg-type]

    outcome = p.run(_state("tag_reclassification"))

    assert outcome.status == "success"
    assert outcome.notes_processed == 2
    assert outcome.watermark_after == {
        "audit_watermark": watermark,
        "retagged": 1,
        "unchanged": 1,
        "low_confidence": 0,
        "failed": 0,
    }
    assert reclassifier.run_watermarks == [watermark]


def test_tag_reclassification_pass_returns_no_work_without_audit_watermark() -> None:
    reclassifier = _FakeTagReclassifier(watermark=None, outcomes=[])
    p = TagReclassificationPass(reclassifier=reclassifier)  # type: ignore[arg-type]

    outcome = p.run(_state("tag_reclassification"))

    assert outcome.status == "no_work"
    assert outcome.notes_processed == 0
    assert outcome.watermark_after == {}


def test_title_quality_pass_has_work_true_when_candidate_matches(tmp_path: Path) -> None:
    store = InMemoryCuratorStore(existing={"kb_x"})
    store.title_quality_queue.append(("kb_x", "How to bootstrap k3s", "body", "topics/k/note/a.md"))
    with httpx.Client(timeout=1.0) as http:
        p = TitleQualityPass(
            store=store,
            vault=_make_vault(tmp_path),
            vault_clone_dir=tmp_path / "vault",
            http_client=http,
            chat_base_url="http://x/v1",
            chat_model="qwen3:32b",
            chat_timeout_seconds=1.0,
        )
        assert p.has_work(_state("title_quality")) is True


def test_title_quality_pass_records_model_and_patterns_in_watermark(tmp_path: Path) -> None:
    """An empty-queue run still stamps the model + patterns hash —
    the next tick can then short-circuit on `wm_model == cur_model`
    instead of paying the SELECT.
    """

    store = InMemoryCuratorStore()
    with httpx.Client(timeout=1.0) as http:
        p = TitleQualityPass(
            store=store,
            vault=_make_vault(tmp_path),
            vault_clone_dir=tmp_path / "vault",
            http_client=http,
            chat_base_url="http://x/v1",
            chat_model="qwen3:32b",
            chat_timeout_seconds=1.0,
            patterns=["^How to "],
        )
        outcome = p.run(_state("title_quality"))
    assert outcome.status == "no_work"
    assert outcome.watermark_after["model"] == "qwen3:32b"
    assert outcome.watermark_after["patterns_hash"] == _patterns_hash(["^How to "])


def test_title_quality_pass_detects_model_drift(tmp_path: Path) -> None:
    """A different `model` in the stored watermark = config drift,
    every matching note re-qualifies even when nothing else changed.
    """

    store = InMemoryCuratorStore(existing={"kb_x"})
    store.title_quality_queue.append(("kb_x", "How to bootstrap k3s", "body", "topics/k/note/a.md"))
    with httpx.Client(timeout=1.0) as http:
        p = TitleQualityPass(
            store=store,
            vault=_make_vault(tmp_path),
            vault_clone_dir=tmp_path / "vault",
            http_client=http,
            chat_base_url="http://x/v1",
            chat_model="qwen3:32b",
            chat_timeout_seconds=1.0,
        )
        stale = _state(
            "title_quality",
            watermark={"model": "qwen3:8b", "patterns_hash": "deadbeef"},
        )
        assert p.has_work(stale) is True


# -------- RelationEnrichmentPass ----------------------------------


@dataclass
class _StubRecall:
    """Test-only recall that returns a fixed list of neighbours."""

    hits: list[RecallHit]

    def recall(
        self,
        *,
        query: str,
        limit: int = 5,
        scope: str | None = None,
    ) -> list[RecallHit]:
        _ = query, scope
        return self.hits[:limit]


def _classify_response(*, see_also: list[str], scope: str = "topic:test") -> dict:
    payload = {
        "title": "Stable old title",
        "scope": scope,
        "topic": "test",
        "type": "note",
        "tags": [],
        "supersedes": [],
        "see_also": see_also,
        "confidence": 0.9,
    }
    return {"choices": [{"message": {"content": json.dumps(payload)}}]}


def test_relation_enrichment_has_work_false_when_no_candidate() -> None:
    store = InMemoryCuratorStore()
    recall = _StubRecall(hits=[])
    with httpx.Client(timeout=1.0) as http:
        p = RelationEnrichmentPass(
            store=store,
            recall=recall,  # type: ignore[arg-type]
            topics=_topics(),
            projects=_projects(),
            http_client=http,
            chat_base_url="http://x/v1",
            chat_model="qwen3:32b",
            chat_timeout_seconds=1.0,
        )
        assert p.has_work(_state("relation_enrichment")) is False


def test_relation_enrichment_writes_validated_see_also_edges() -> None:
    store = InMemoryCuratorStore(existing={"kb_old01", "kb_neighbour1"})
    store.relation_enrichment_queue.append(("kb_old01", "Vault Raft unseal", "Body.", "topic:test"))
    recall = _StubRecall(
        hits=[
            RecallHit(
                id="kb_neighbour1",
                type="note",
                scope="topic:test",
                title="Vault Raft snapshot restore",
                snippet="...",
                score=0.9,
            ),
        ]
    )

    base_url = "http://ollama-heavy.test/v1"
    with respx.mock(base_url=base_url) as mock_router:
        mock_router.post("/chat/completions").respond(
            200,
            json=_classify_response(see_also=["kb_neighbour1", "kb_unknown"]),
        )
        with httpx.Client(timeout=10.0) as http:
            p = RelationEnrichmentPass(
                store=store,
                recall=recall,  # type: ignore[arg-type]
                topics=_topics(),
                projects=_projects(),
                http_client=http,
                chat_base_url=base_url,
                chat_model="qwen3:32b",
                chat_timeout_seconds=10.0,
            )
            outcome = p.run(_state("relation_enrichment"))

    # `kb_neighbour1` exists → edge written. `kb_unknown` filtered
    # out by `existing_ids`.
    assert outcome.status == "success"
    assert outcome.notes_processed == 1
    edges = [r for r in store.relations if r[1] == "see_also"]
    assert edges == [("kb_old01", "see_also", "kb_neighbour1")]
    assert outcome.watermark_after["model"] == "qwen3:32b"


def test_relation_enrichment_run_returns_no_work_on_empty_candidate_set() -> None:
    store = InMemoryCuratorStore()
    recall = _StubRecall(hits=[])
    with httpx.Client(timeout=1.0) as http:
        p = RelationEnrichmentPass(
            store=store,
            recall=recall,  # type: ignore[arg-type]
            topics=_topics(),
            projects=_projects(),
            http_client=http,
            chat_base_url="http://x/v1",
            chat_model="qwen3:32b",
            chat_timeout_seconds=1.0,
        )
        outcome = p.run(_state("relation_enrichment"))
    assert outcome.status == "no_work"
    assert outcome.notes_processed == 0


def test_relation_enrichment_skips_self_match_in_recall_hits() -> None:
    """A recall result that includes the candidate itself must not
    end up in the neighbour list — the LLM would otherwise see the
    note quoting itself, which biases the see_also choice.
    """

    store = InMemoryCuratorStore(existing={"kb_self", "kb_other"})
    store.relation_enrichment_queue.append(("kb_self", "Self title", "body", "topic:test"))
    recall = _StubRecall(
        hits=[
            RecallHit(
                id="kb_self",
                type="note",
                scope="topic:test",
                title="Self title",
                snippet="...",
                score=0.99,
            ),
            RecallHit(
                id="kb_other",
                type="note",
                scope="topic:test",
                title="Other",
                snippet="...",
                score=0.5,
            ),
        ]
    )
    base_url = "http://ollama-heavy.test/v1"
    with respx.mock(base_url=base_url) as mock_router:
        mock_router.post("/chat/completions").respond(
            200, json=_classify_response(see_also=["kb_other"])
        )
        with httpx.Client(timeout=5.0) as http:
            p = RelationEnrichmentPass(
                store=store,
                recall=recall,  # type: ignore[arg-type]
                topics=_topics(),
                projects=_projects(),
                http_client=http,
                chat_base_url=base_url,
                chat_model="qwen3:32b",
                chat_timeout_seconds=5.0,
            )
            outcome = p.run(_state("relation_enrichment"))
    assert outcome.notes_processed == 1
    assert ("kb_self", "see_also", "kb_other") in store.relations


def test_relation_enrichment_soft_fails_on_recall_outage() -> None:
    """When the recall service raises, the pass classifies with an
    empty neighbour list rather than aborting the whole tick.
    """

    @dataclass
    class _BrokenRecall:
        def recall(
            self,
            *,
            query: str,
            limit: int = 5,
            scope: str | None = None,
        ) -> list[RecallHit]:
            _ = query, limit, scope
            raise RuntimeError("recall down")

    store = InMemoryCuratorStore(existing={"kb_a"})
    store.relation_enrichment_queue.append(("kb_a", "Some title", "body", "topic:test"))
    base_url = "http://ollama-heavy.test/v1"
    with respx.mock(base_url=base_url) as mock_router:
        mock_router.post("/chat/completions").respond(200, json=_classify_response(see_also=[]))
        with httpx.Client(timeout=5.0) as http:
            p = RelationEnrichmentPass(
                store=store,
                recall=_BrokenRecall(),  # type: ignore[arg-type]
                topics=_topics(),
                projects=_projects(),
                http_client=http,
                chat_base_url=base_url,
                chat_model="qwen3:32b",
                chat_timeout_seconds=5.0,
            )
            outcome = p.run(_state("relation_enrichment"))
    assert outcome.status == "success"
    assert outcome.notes_processed == 1


def test_relation_enrichment_swallows_classify_errors() -> None:
    """One bad classification must not poison the rest of the batch."""

    store = InMemoryCuratorStore(existing={"kb_a", "kb_b"})
    store.relation_enrichment_queue.extend(
        [
            ("kb_a", "A title", "body", "topic:test"),
            ("kb_b", "B title", "body", "topic:test"),
        ]
    )
    recall = _StubRecall(hits=[])
    base_url = "http://ollama-heavy.test/v1"
    with respx.mock(base_url=base_url) as mock_router:
        # First call: broken JSON forces a Classification ValidationError
        # after the one built-in retry. Second call: clean response.
        mock_router.post("/chat/completions").mock(
            side_effect=[
                httpx.Response(200, json={"choices": [{"message": {"content": "not json"}}]}),
                httpx.Response(200, json={"choices": [{"message": {"content": "not json"}}]}),
                httpx.Response(200, json=_classify_response(see_also=[])),
            ]
        )
        with httpx.Client(timeout=5.0) as http:
            p = RelationEnrichmentPass(
                store=store,
                recall=recall,  # type: ignore[arg-type]
                topics=_topics(),
                projects=_projects(),
                http_client=http,
                chat_base_url=base_url,
                chat_model="qwen3:32b",
                chat_timeout_seconds=5.0,
            )
            outcome = p.run(_state("relation_enrichment"))
    # `kb_a` is dropped (classifier-after-retry failure); `kb_b` is
    # processed. Pass-level outcome reports the survivor.
    assert outcome.notes_processed == 1


def test_relation_enrichment_skips_when_candidate_already_has_see_also() -> None:
    store = InMemoryCuratorStore(existing={"kb_done"})
    store.relation_enrichment_queue.append(("kb_done", "Some title", "body", "topic:test"))
    # `kb_done` already has an outgoing see_also — the LEFT JOIN ...
    # IS NULL filter excludes it.
    store.relations.append(("kb_done", "see_also", "kb_other"))

    recall = _StubRecall(hits=[])
    with httpx.Client(timeout=1.0) as http:
        p = RelationEnrichmentPass(
            store=store,
            recall=recall,  # type: ignore[arg-type]
            topics=_topics(),
            projects=_projects(),
            http_client=http,
            chat_base_url="http://x/v1",
            chat_model="qwen3:32b",
            chat_timeout_seconds=1.0,
        )
        assert p.has_work(_state("relation_enrichment")) is False
