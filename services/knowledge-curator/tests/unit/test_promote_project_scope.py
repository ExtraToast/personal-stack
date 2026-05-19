"""Promoter tests for the closed `project:<slug>` vocabulary.

Mirror the topic-vocab tests in `test_promote.py` but exercise the
`ProjectVocabulary` path: known slug, alias substitution, unknown
slug routes to needs-review.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import pytest

from curator.classify import Classification, Neighbour
from curator.projects import Project, ProjectVocabulary
from curator.promote import Promoter
from curator.recall import RecallHit
from curator.store import InMemoryCuratorStore
from curator.topics import Topic, TopicVocabulary
from curator.vault import PromotionResult


class _StubVault:
    def __init__(self) -> None:
        self.promotions: list[tuple[str, str, str, str]] = []
        self.reviews: list[tuple[str, str]] = []

    def promote(
        self,
        *,
        source_rel: str,
        destination_rel: str,
        new_body: str,
        commit_subject: str,
    ) -> PromotionResult:
        self.promotions.append((source_rel, destination_rel, new_body, commit_subject))
        return PromotionResult(new_relative_path=destination_rel, commit_sha="a" * 40)

    def move_to_needs_review(self, *, source_rel: str, reason: str) -> PromotionResult:
        self.reviews.append((source_rel, reason))
        return PromotionResult(
            new_relative_path=f"_inbox/_needs-review/{Path(source_rel).name}",
            commit_sha="b" * 40,
        )


@dataclass
class _StubClassifier:
    response: Classification

    def classify(
        self,
        *,
        title: str,
        body: str,
        neighbours: list[Neighbour],
        inbox_scope_hint: str | None = None,
    ) -> Classification:
        return self.response


@dataclass
class _StubRecall:
    hits: list[RecallHit]

    def recall(
        self,
        *,
        query: str,
        limit: int = 5,
        scope: str | None = None,
    ) -> list[RecallHit]:
        return self.hits


def _seed_inbox(clone: Path, scope: str = "_inbox") -> str:
    inbox = clone / "_inbox" / "2026-05-19"
    inbox.mkdir(parents=True, exist_ok=True)
    rel = "_inbox/2026-05-19/120000-some-note--01HXYZ99.md"
    body = (
        "---\n"
        "id: 01HXYZ99000000000000000000\n"
        "type: note\n"
        f"scope: {scope}\n"
        "source: claude-code\n"
        "captured_at: 2026-05-19T12:00:00+00:00\n"
        "confidence: 0.5\n"
        "---\n\n# Some note\n\nbody\n"
    )
    (clone / rel).write_text(body, encoding="utf-8")
    return rel


def _topics() -> TopicVocabulary:
    return TopicVocabulary([Topic(slug="kotlin")])


def _projects() -> ProjectVocabulary:
    return ProjectVocabulary(
        [
            Project(
                slug="extratoast/personal-stack",
                aliases=("personal-stack", "personal-stack-2", "homelab", "home-direct"),
            ),
            Project(
                slug="esa-blueshell/website",
                aliases=(
                    "website",
                    "esa-blueshell-website",
                    "esa-blueshell.website",
                ),
            ),
        ],
    )


def _classification(scope: str) -> Classification:
    return Classification.model_validate(
        {
            "title": "Some short title",
            "scope": scope,
            "topic": None,
            "type": "note",
            "tags": [],
            "supersedes": [],
            "see_also": [],
            "confidence": 0.85,
            "needs_review_reason": None,
        }
    )


@pytest.fixture()
def clone(tmp_path: Path) -> Path:
    return tmp_path


def _build(
    promoter_clone: Path, classifier_scope: str
) -> tuple[Promoter, _StubVault, InMemoryCuratorStore]:
    vault = _StubVault()
    store = InMemoryCuratorStore(existing=["01HXYZ99000000000000000000"])
    promoter = Promoter(
        classifier=_StubClassifier(response=_classification(classifier_scope)),  # type: ignore[arg-type]
        recall=_StubRecall(hits=[]),  # type: ignore[arg-type]
        store=store,
        vault=vault,  # type: ignore[arg-type]
        topics=_topics(),
        projects=_projects(),
        clone_dir=promoter_clone,
        confidence_floor=0.55,
        recall_limit=3,
    )
    return promoter, vault, store


def test_known_org_repo_scope_promotes_into_org_subfolder(clone: Path) -> None:
    rel = _seed_inbox(clone)
    promoter, vault, store = _build(clone, classifier_scope="project:extratoast/personal-stack")

    outcome = promoter.promote_inbox_file(rel)

    assert outcome.status == "promoted"
    # Vault grouping by org: every ExtraToast repo sits under
    # projects/extratoast/.
    assert outcome.destination_rel.startswith("projects/extratoast/personal-stack/")
    assert store.promotions[0]["scope"] == "project:extratoast/personal-stack"
    assert "project:extratoast/personal-stack" in vault.promotions[0][3]


def test_bare_repo_alias_canonicalises_to_org_repo(clone: Path) -> None:
    # Classifier emits `project:personal-stack` (no org). Vocabulary
    # aliases the bare repo to `extratoast/personal-stack` so the
    # promotion lands under `projects/extratoast/personal-stack/`.
    rel = _seed_inbox(clone)
    promoter, vault, store = _build(clone, classifier_scope="project:personal-stack")

    outcome = promoter.promote_inbox_file(rel)

    assert outcome.status == "promoted"
    assert outcome.destination_rel.startswith("projects/extratoast/personal-stack/")
    assert store.promotions[0]["scope"] == "project:extratoast/personal-stack"
    assert "project:extratoast/personal-stack" in vault.promotions[0][3]


def test_personal_stack_2_alias_canonicalises_to_real_repo(clone: Path) -> None:
    # IDE working-copy suffix `personal-stack-2` rescued via alias.
    rel = _seed_inbox(clone)
    promoter, _vault, store = _build(clone, classifier_scope="project:personal-stack-2")

    outcome = promoter.promote_inbox_file(rel)

    assert outcome.status == "promoted"
    assert outcome.destination_rel.startswith("projects/extratoast/personal-stack/")
    assert "personal-stack-2" not in outcome.destination_rel
    assert store.promotions[0]["scope"] == "project:extratoast/personal-stack"


def test_org_dash_repo_alias_canonicalises_to_org_repo(clone: Path) -> None:
    # Production saw `project:esa-blueshell-website` (org+repo
    # mashed with a dash). Vocabulary alias routes to canonical
    # `esa-blueshell/website` so the file lands under
    # `projects/esa-blueshell/website/`.
    rel = _seed_inbox(clone)
    promoter, _vault, store = _build(clone, classifier_scope="project:esa-blueshell-website")

    outcome = promoter.promote_inbox_file(rel)

    assert outcome.status == "promoted"
    assert outcome.destination_rel.startswith("projects/esa-blueshell/website/")
    assert store.promotions[0]["scope"] == "project:esa-blueshell/website"


def test_unknown_project_scope_routes_to_needs_review(clone: Path) -> None:
    # `github-actions` looks like a repo but isn't one of our projects.
    # Previously the curator happily created `projects/github-actions/`.
    # Now: needs-review with a clear reason.
    rel = _seed_inbox(clone)
    promoter, vault, store = _build(clone, classifier_scope="project:github-actions")

    outcome = promoter.promote_inbox_file(rel)

    assert outcome.status == "needs_review"
    assert outcome.reason == "unknown-project-slug:github-actions"
    assert vault.promotions == []  # never wrote to disk under projects/
    assert store.promotions == []


def test_unknown_project_includes_my_kubernetes_observability_stack(clone: Path) -> None:
    rel = _seed_inbox(clone)
    promoter, vault, _ = _build(
        clone,
        classifier_scope="project:my-kubernetes-observability-stack",
    )

    outcome = promoter.promote_inbox_file(rel)

    assert outcome.status == "needs_review"
    assert outcome.reason == "unknown-project-slug:my-kubernetes-observability-stack"
    assert vault.promotions == []


def test_agent_scope_passes_through_unchanged(clone: Path) -> None:
    rel = _seed_inbox(clone)
    promoter, _, store = _build(clone, classifier_scope="agent:_shared")

    outcome = promoter.promote_inbox_file(rel)

    assert outcome.status == "promoted"
    assert outcome.destination_rel.startswith("agents/_shared/")
    assert store.promotions[0]["scope"] == "agent:_shared"


def test_empty_project_vocabulary_routes_every_project_scope_to_review(clone: Path) -> None:
    rel = _seed_inbox(clone)
    vault = _StubVault()
    store = InMemoryCuratorStore(existing=["01HXYZ99000000000000000000"])
    promoter = Promoter(
        classifier=_StubClassifier(  # type: ignore[arg-type]
            response=_classification("project:extratoast/personal-stack"),
        ),
        recall=_StubRecall(hits=[]),  # type: ignore[arg-type]
        store=store,
        vault=vault,  # type: ignore[arg-type]
        topics=_topics(),
        projects=ProjectVocabulary([]),
        clone_dir=clone,
        confidence_floor=0.55,
        recall_limit=3,
    )

    outcome = promoter.promote_inbox_file(rel)

    assert outcome.status == "needs_review"
    assert outcome.reason == "unknown-project-slug:extratoast/personal-stack"
