"""Unit tests for TitleQualityPass + RelationEnrichmentPass."""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

import httpx
import respx
from git import Actor, Repo

from curator.orchestrator.passes.relation_enrichment import RelationEnrichmentPass
from curator.orchestrator.passes.title_quality import TitleQualityPass, _patterns_hash
from curator.orchestrator.protocol import PassState
from curator.projects import Project, ProjectVocabulary
from curator.recall import RecallHit
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
