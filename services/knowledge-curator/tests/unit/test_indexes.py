from __future__ import annotations

from pathlib import Path

from curator.indexes import (
    ConflictEdge,
    IndexedNote,
    load_promoted_notes,
    render_conflicts,
    render_recent,
    render_topic_moc,
    topics_present,
    write_conflicts,
    write_recent,
    write_topic_mocs,
)


def _note(rel: str, title: str, scope: str, type_: str = "lesson", id_: str = "01H") -> IndexedNote:
    return IndexedNote(
        id=id_,
        rel_path=rel,
        type=type_,
        scope=scope,
        title=title,
        captured_at="2026-05-18T12:00:00+00:00",
    )


def test_load_promoted_notes_skips_inbox_and_index(tmp_path: Path) -> None:
    _seed(
        tmp_path / "topics/vault/lesson/a.md",
        id_="01HA0",
        type_="lesson",
        scope="topic:vault",
        title="Vault note",
    )
    _seed(
        tmp_path / "_inbox/2026-05-18/120000-b--01HB0.md",
        id_="01HB0",
        type_="lesson",
        scope="_inbox",
        title="Inbox note",
    )
    _seed(
        tmp_path / "_index/recent.md",
        id_="kb_index_recent",
        type_="note",
        scope="agent:_shared",
        title="Recent",
    )
    out = load_promoted_notes(tmp_path)
    assert [n.id for n in out] == ["01HA0"]


def _seed(path: Path, *, id_: str, type_: str, scope: str, title: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        "---\n"
        f"id: {id_}\n"
        f"type: {type_}\n"
        f"scope: {scope}\n"
        "captured_at: 2026-05-18T12:00:00+00:00\n"
        "---\n"
        "\n"
        f"# {title}\n"
        "\n"
        "body\n",
        encoding="utf-8",
    )


def test_render_recent_lists_newest_first_with_wikilinks() -> None:
    notes = [
        IndexedNote(
            id="01A",
            rel_path="topics/vault/lesson/a.md",
            type="lesson",
            scope="topic:vault",
            title="A",
            captured_at="2026-05-13T12:00:00+00:00",
        ),
        IndexedNote(
            id="01B",
            rel_path="topics/vault/lesson/b.md",
            type="lesson",
            scope="topic:vault",
            title="B",
            captured_at="2026-05-18T12:00:00+00:00",
        ),
    ]
    out = render_recent(notes)
    # Newest entry (B) precedes older (A) in the wikilink list.
    a_pos = out.find("|A]]")
    b_pos = out.find("|B]]")
    assert 0 < b_pos < a_pos


def test_render_recent_uses_empty_placeholder_when_empty() -> None:
    out = render_recent([])
    assert "no promoted notes yet" in out


def test_topics_present_extracts_unique_topic_slugs() -> None:
    notes = [
        _note("topics/vault/lesson/a.md", "A", "topic:vault"),
        _note("topics/vault/lesson/b.md", "B", "topic:vault"),
        _note("topics/kotlin/lesson/c.md", "C", "topic:kotlin"),
        _note("projects/x/lesson/d.md", "D", "project:x"),
    ]
    assert topics_present(notes) == {"vault", "kotlin"}


def test_render_topic_moc_groups_by_type() -> None:
    notes = [
        _note("topics/vault/lesson/a.md", "Lesson A", "topic:vault", "lesson"),
        _note("topics/vault/decision/b.md", "Decision B", "topic:vault", "decision"),
        _note("topics/kotlin/lesson/c.md", "Other", "topic:kotlin", "lesson"),
    ]
    out = render_topic_moc("vault", notes)
    assert "# Topic: vault" in out
    assert "## Lessons" in out
    assert "## Decisions" in out
    assert "Lesson A" in out
    assert "Decision B" in out
    assert "Other" not in out  # different topic


def test_render_conflicts_links_known_ids_and_falls_back_to_raw() -> None:
    edges = [
        ConflictEdge(subject_id="01A", predicate="supersedes", object_id="01B"),
        ConflictEdge(subject_id="01C", predicate="contradicts", object_id="01MISSING"),
    ]
    notes = [
        _note("topics/vault/lesson/a.md", "A note", "topic:vault", id_="01A"),
        _note("topics/vault/lesson/b.md", "B note", "topic:vault", id_="01B"),
        _note("topics/vault/lesson/c.md", "C note", "topic:vault", id_="01C"),
    ]
    out = render_conflicts(edges, notes)
    assert "## Supersedes" in out
    assert "## Contradicts" in out
    assert "[[topics/vault/lesson/a.md|A note]]" in out
    assert "`01MISSING`" in out


def test_render_conflicts_empty_placeholder_when_no_edges() -> None:
    out = render_conflicts([], [])
    assert "no outstanding conflicts" in out


def test_writers_create_files_under_index(tmp_path: Path) -> None:
    notes = [_note("topics/vault/lesson/a.md", "A", "topic:vault")]
    edges = [ConflictEdge(subject_id="01H", predicate="supersedes", object_id="01HX")]
    write_recent(tmp_path, notes)
    write_topic_mocs(tmp_path, notes)
    write_conflicts(tmp_path, edges, notes)
    assert (tmp_path / "_index" / "recent.md").exists()
    assert (tmp_path / "_index" / "topics" / "vault.md").exists()
    assert (tmp_path / "_index" / "conflicts.md").exists()
