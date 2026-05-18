"""Promoter orchestration tests.

The vault git path is exercised in test_vault.py with a real Repo;
here we stub it with a Protocol-compatible double so we focus on
the branching logic: when does the promoter promote, when does it
flag for review, and how do `supersedes` / `see_also` validations
interact with the store?
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import pytest

from curator.classify import Classification, ClassificationError, Neighbour
from curator.promote import Promoter
from curator.recall import RecallHit
from curator.store import InMemoryCuratorStore
from curator.topics import Topic, TopicVocabulary
from curator.vault import PromotionResult

# -------- doubles ---------------------------------------------------


class _StubVault:
    def __init__(self) -> None:
        self.promotions: list[tuple[str, str, str, str]] = []
        self.reviews: list[tuple[str, str]] = []

    def promote(
        self, *, source_rel: str, destination_rel: str, new_body: str, commit_subject: str
    ) -> PromotionResult:
        self.promotions.append((source_rel, destination_rel, new_body, commit_subject))
        return PromotionResult(new_relative_path=destination_rel, commit_sha="a" * 40)

    def move_to_needs_review(self, *, source_rel: str, reason: str) -> PromotionResult:
        self.reviews.append((source_rel, reason))
        return PromotionResult(
            new_relative_path=f"_inbox/_needs-review/{Path(source_rel).name}", commit_sha="b" * 40
        )


@dataclass
class _StubClassifier:
    response: Classification | Exception

    def classify(
        self,
        *,
        title: str,
        body: str,
        neighbours: list[Neighbour],
        inbox_scope_hint: str | None = None,
    ) -> Classification:
        if isinstance(self.response, Exception):
            raise self.response
        return self.response


@dataclass
class _StubRecall:
    hits: list[RecallHit]

    def recall(self, *, query: str, limit: int = 5, scope: str | None = None) -> list[RecallHit]:
        return self.hits


def _classification(**overrides: object) -> Classification:
    base = {
        "title": "Vault Agent uid alignment",
        "scope": "topic:vault",
        "topic": "vault",
        "type": "lesson",
        "tags": ["vault", "kubernetes"],
        "supersedes": [],
        "see_also": [],
        "confidence": 0.85,
        "needs_review_reason": None,
    }
    base.update(overrides)
    return Classification.model_validate(base)


def _seed_inbox_note(clone: Path, body_lines: list[str] | None = None) -> str:
    inbox = clone / "_inbox" / "2026-05-13"
    inbox.mkdir(parents=True, exist_ok=True)
    rel = "_inbox/2026-05-13/120000-vault-agent-uid-alignment--01HXYZ00.md"
    body = "\n".join(
        body_lines
        or [
            "---",
            "id: 01HXYZ00000000000000000000",
            "type: lesson",
            "scope: _inbox",
            "source: claude-code",
            "captured_at: 2026-05-13T12:00:00+00:00",
            "confidence: 0.4",
            "---",
            "",
            "# Vault Agent uid alignment",
            "",
            "body line",
            "",
        ]
    )
    (clone / rel).write_text(body, encoding="utf-8")
    return rel


@pytest.fixture()
def clone(tmp_path: Path) -> Path:
    return tmp_path


def _topics() -> TopicVocabulary:
    return TopicVocabulary([Topic(slug="vault"), Topic(slug="kubernetes")])


def test_promote_happy_path_updates_store_and_vault(clone: Path) -> None:
    rel = _seed_inbox_note(clone)
    vault = _StubVault()
    store = InMemoryCuratorStore(existing=["01HXYZ00000000000000000000"])
    classifier = _StubClassifier(response=_classification())
    promoter = Promoter(
        classifier=classifier,  # type: ignore[arg-type]
        recall=_StubRecall(hits=[]),  # type: ignore[arg-type]
        store=store,
        vault=vault,  # type: ignore[arg-type]
        topics=_topics(),
        clone_dir=clone,
        confidence_floor=0.55,
        recall_limit=3,
    )
    outcome = promoter.promote_inbox_file(rel)
    assert outcome.status == "promoted"
    assert outcome.destination_rel == "topics/vault/lesson/vault-agent-uid-alignment.md"
    assert vault.promotions[0][1] == outcome.destination_rel
    assert store.promotions[0]["scope"] == "topic:vault"


def test_promote_flags_review_when_confidence_below_floor(clone: Path) -> None:
    rel = _seed_inbox_note(clone)
    vault = _StubVault()
    promoter = Promoter(
        classifier=_StubClassifier(response=_classification(confidence=0.3)),  # type: ignore[arg-type]
        recall=_StubRecall(hits=[]),  # type: ignore[arg-type]
        store=InMemoryCuratorStore(existing=["01HXYZ00000000000000000000"]),
        vault=vault,  # type: ignore[arg-type]
        topics=_topics(),
        clone_dir=clone,
        confidence_floor=0.55,
        recall_limit=3,
    )
    outcome = promoter.promote_inbox_file(rel)
    assert outcome.status == "needs_review"
    assert "low-confidence" in outcome.reason
    assert vault.reviews and not vault.promotions


def test_promote_flags_review_on_unknown_topic_slug(clone: Path) -> None:
    rel = _seed_inbox_note(clone)
    classification = _classification(scope="topic:made-up", topic="made-up")
    vault = _StubVault()
    promoter = Promoter(
        classifier=_StubClassifier(response=classification),  # type: ignore[arg-type]
        recall=_StubRecall(hits=[]),  # type: ignore[arg-type]
        store=InMemoryCuratorStore(existing=["01HXYZ00000000000000000000"]),
        vault=vault,  # type: ignore[arg-type]
        topics=_topics(),
        clone_dir=clone,
        confidence_floor=0.55,
        recall_limit=3,
    )
    outcome = promoter.promote_inbox_file(rel)
    assert outcome.status == "needs_review"
    assert "unknown-topic-slug" in outcome.reason


def test_promote_flags_review_when_supersedes_target_missing(clone: Path) -> None:
    rel = _seed_inbox_note(clone)
    classification = _classification(supersedes=["01MISSING0000000000000000"])
    vault = _StubVault()
    promoter = Promoter(
        classifier=_StubClassifier(response=classification),  # type: ignore[arg-type]
        recall=_StubRecall(hits=[]),  # type: ignore[arg-type]
        store=InMemoryCuratorStore(existing=["01HXYZ00000000000000000000"]),
        vault=vault,  # type: ignore[arg-type]
        topics=_topics(),
        clone_dir=clone,
        confidence_floor=0.55,
        recall_limit=3,
    )
    outcome = promoter.promote_inbox_file(rel)
    assert outcome.status == "needs_review"
    assert "relation-target-missing" in outcome.reason


def test_promote_flags_review_on_classifier_error(clone: Path) -> None:
    rel = _seed_inbox_note(clone)
    vault = _StubVault()
    promoter = Promoter(
        classifier=_StubClassifier(response=ClassificationError("boom")),  # type: ignore[arg-type]
        recall=_StubRecall(hits=[]),  # type: ignore[arg-type]
        store=InMemoryCuratorStore(existing=["01HXYZ00000000000000000000"]),
        vault=vault,  # type: ignore[arg-type]
        topics=_topics(),
        clone_dir=clone,
        confidence_floor=0.55,
        recall_limit=3,
    )
    outcome = promoter.promote_inbox_file(rel)
    assert outcome.status == "needs_review"
    assert "classify-failed" in outcome.reason


def test_promote_supersedes_inserts_relations(clone: Path) -> None:
    rel = _seed_inbox_note(clone)
    store = InMemoryCuratorStore(
        existing=[
            "01HXYZ00000000000000000000",
            "01HABC0000000000000000000",
            "01HDEF0000000000000000000",
        ]
    )
    classification = _classification(
        supersedes=["01HABC0000000000000000000"],
        see_also=["01HDEF0000000000000000000"],
    )
    vault = _StubVault()
    promoter = Promoter(
        classifier=_StubClassifier(response=classification),  # type: ignore[arg-type]
        recall=_StubRecall(hits=[]),  # type: ignore[arg-type]
        store=store,
        vault=vault,  # type: ignore[arg-type]
        topics=_topics(),
        clone_dir=clone,
        confidence_floor=0.55,
        recall_limit=3,
    )
    promoter.promote_inbox_file(rel)
    assert (
        "01HXYZ00000000000000000000",
        "supersedes",
        "01HABC0000000000000000000",
    ) in store.relations
    assert (
        "01HXYZ00000000000000000000",
        "see_also",
        "01HDEF0000000000000000000",
    ) in store.relations
