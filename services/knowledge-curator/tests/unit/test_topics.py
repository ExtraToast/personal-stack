from __future__ import annotations

from pathlib import Path
from typing import Any

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


# ---- DB-backed loader (kb_topics + kb_topic_aliases) ----


class _FakeCursor:
    """Stand-in for ``psycopg.Cursor`` that returns scripted rows per
    SQL prefix. Avoids spinning up Testcontainers Postgres for a
    pure parse-and-return path.
    """

    def __init__(self, rows_by_prefix: dict[str, list[tuple[Any, ...]]]) -> None:
        self._rows_by_prefix = rows_by_prefix
        self._fetched: list[tuple[Any, ...]] = []

    def execute(self, sql: str, params: tuple[Any, ...] | None = None) -> None:
        del params
        for prefix, rows in self._rows_by_prefix.items():
            if sql.startswith(prefix) or prefix in sql:
                self._fetched = rows
                return
        self._fetched = []

    def fetchall(self) -> list[tuple[Any, ...]]:
        return list(self._fetched)

    def __enter__(self) -> _FakeCursor:
        return self

    def __exit__(self, *exc: Any) -> None:
        return None


class _FakeConnection:
    def __init__(self, rows_by_prefix: dict[str, list[tuple[Any, ...]]]) -> None:
        self._rows_by_prefix = rows_by_prefix

    def cursor(self) -> _FakeCursor:
        return _FakeCursor(self._rows_by_prefix)


def test_from_db_returns_empty_vocabulary_when_table_is_empty() -> None:
    conn = _FakeConnection({"SELECT slug, description": []})
    vocab = TopicVocabulary.from_db(conn)  # type: ignore[arg-type]
    assert vocab.slugs == ()


def test_from_db_loads_active_topics_with_aliases() -> None:
    conn = _FakeConnection(
        {
            "SELECT slug, description": [
                ("kotlin", "JVM language"),
                ("python", "Snake language"),
            ],
            "SELECT slug, alias_lower": [
                ("kotlin", "kt"),
                ("python", "py"),
            ],
        }
    )
    vocab = TopicVocabulary.from_db(conn)  # type: ignore[arg-type]
    assert vocab.slugs == ("kotlin", "python")
    assert vocab.slug_for("kotlin") == "kotlin"
    assert vocab.slug_for("kt") == "kotlin"
    assert vocab.slug_for("py") == "python"
    # An alias rooted on a slug that's not in the active list is
    # ignored — the alias filter on the inner query already excludes
    # it, this is a belt-and-braces assertion.
    assert vocab.slug_for("ruby") is None
