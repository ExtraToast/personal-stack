from __future__ import annotations

from pathlib import Path

import pytest

from curator.topics import TopicVocabulary


def _write(path: Path, body: str) -> Path:
    path.write_text(body, encoding="utf-8")
    return path


def test_from_yaml_parses_slug_and_aliases(tmp_path: Path) -> None:
    cfg = _write(
        tmp_path / "topics.yaml",
        """
topics:
  - slug: kotlin
    aliases: [Kotlin, kt]
    description: JVM language
  - slug: python
""",
    )
    vocab = TopicVocabulary.from_yaml(cfg)
    assert vocab.slugs == ("kotlin", "python")
    assert vocab.slug_for("kotlin") == "kotlin"
    assert vocab.slug_for("Kotlin") == "kotlin"
    assert vocab.slug_for("kt") == "kotlin"
    assert vocab.slug_for("python") == "python"
    assert vocab.slug_for("ruby") is None


def test_duplicate_slug_is_rejected(tmp_path: Path) -> None:
    cfg = _write(
        tmp_path / "topics.yaml",
        """
topics:
  - slug: kotlin
  - slug: kotlin
""",
    )
    with pytest.raises(ValueError, match="Duplicate topic slug"):
        TopicVocabulary.from_yaml(cfg)


def test_duplicate_alias_across_topics_is_rejected(tmp_path: Path) -> None:
    cfg = _write(
        tmp_path / "topics.yaml",
        """
topics:
  - slug: kotlin
    aliases: [jvm]
  - slug: java
    aliases: [JVM]
""",
    )
    with pytest.raises(ValueError, match="Duplicate topic alias"):
        TopicVocabulary.from_yaml(cfg)


def test_missing_slug_field_is_rejected(tmp_path: Path) -> None:
    cfg = _write(
        tmp_path / "topics.yaml",
        """
topics:
  - description: oops
""",
    )
    with pytest.raises(ValueError, match="missing 'slug'"):
        TopicVocabulary.from_yaml(cfg)


def test_top_level_must_have_topics_list(tmp_path: Path) -> None:
    cfg = _write(tmp_path / "topics.yaml", "not_topics: []\n")
    with pytest.raises(ValueError, match="'topics' key"):
        TopicVocabulary.from_yaml(cfg)
