"""Unit tests for the closed project vocabulary loader."""

from __future__ import annotations

from pathlib import Path

import pytest

from curator.projects import Project, ProjectVocabulary


def test_vocab_returns_canonical_org_repo_slug_for_an_alias() -> None:
    vocab = ProjectVocabulary(
        [
            Project(
                slug="extratoast/personal-stack",
                aliases=("personal-stack", "personal-stack-2", "homelab"),
            ),
            Project(
                slug="esa-blueshell/website",
                aliases=("website", "esa-blueshell-website", "blueshell-website"),
            ),
        ]
    )
    # Canonical form maps to itself.
    assert vocab.slug_for("extratoast/personal-stack") == "extratoast/personal-stack"
    # Bare repo name aliases to org/repo.
    assert vocab.slug_for("personal-stack") == "extratoast/personal-stack"
    # IDE working-copy suffix gets rescued too.
    assert vocab.slug_for("personal-stack-2") == "extratoast/personal-stack"
    # Case-insensitive.
    assert vocab.slug_for("Personal-Stack-2") == "extratoast/personal-stack"
    assert vocab.slug_for("homelab") == "extratoast/personal-stack"
    # ESA-Blueshell repo: bare + org-mashed-dash forms.
    assert vocab.slug_for("esa-blueshell/website") == "esa-blueshell/website"
    assert vocab.slug_for("website") == "esa-blueshell/website"
    assert vocab.slug_for("esa-blueshell-website") == "esa-blueshell/website"


def test_vocab_returns_none_for_unknown_slug() -> None:
    vocab = ProjectVocabulary([Project(slug="extratoast/personal-stack")])
    assert vocab.slug_for("github-actions") is None
    assert vocab.slug_for("my-kubernetes-observability-stack") is None
    assert vocab.slug_for("") is None


def test_vocab_rejects_duplicate_slugs() -> None:
    with pytest.raises(ValueError, match="Duplicate project slug"):
        ProjectVocabulary(
            [
                Project(slug="extratoast/personal-stack"),
                Project(slug="extratoast/personal-stack"),
            ]
        )


def test_vocab_rejects_alias_that_maps_to_two_slugs() -> None:
    with pytest.raises(ValueError, match="Duplicate project alias"):
        ProjectVocabulary(
            [
                Project(slug="extratoast/personal-stack", aliases=("home-direct",)),
                Project(slug="esa-blueshell/website", aliases=("home-direct",)),
            ]
        )


def test_vocab_empty_is_fine() -> None:
    vocab = ProjectVocabulary([])
    assert vocab.slugs == ()
    assert vocab.slug_for("anything") is None


def test_vocab_from_yaml_round_trips(tmp_path: Path) -> None:
    src = tmp_path / "projects.yaml"
    src.write_text(
        """
projects:
  - slug: extratoast/personal-stack
    aliases: [personal-stack, personal-stack-2, homelab]
    github_org: ExtraToast
    description: Homelab GitOps monorepo.
  - slug: esa-blueshell/website
    aliases: [website, esa-blueshell-website]
    github_org: ESA-Blueshell
""",
        encoding="utf-8",
    )
    vocab = ProjectVocabulary.from_yaml(src)
    assert set(vocab.slugs) == {"extratoast/personal-stack", "esa-blueshell/website"}
    assert vocab.slug_for("personal-stack-2") == "extratoast/personal-stack"
    by_slug = {p.slug: p for p in vocab.projects}
    assert by_slug["extratoast/personal-stack"].github_org == "ExtraToast"
    assert by_slug["esa-blueshell/website"].github_org == "ESA-Blueshell"


def test_vocab_from_yaml_rejects_missing_slug(tmp_path: Path) -> None:
    src = tmp_path / "bad.yaml"
    src.write_text("projects:\n  - aliases: [foo]\n", encoding="utf-8")
    with pytest.raises(ValueError, match="missing 'slug'"):
        ProjectVocabulary.from_yaml(src)
