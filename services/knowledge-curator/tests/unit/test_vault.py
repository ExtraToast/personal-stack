from __future__ import annotations

from pathlib import Path

import pytest
from git import Actor, Repo

from curator.vault import (
    CuratorVault,
    folder_for_scope,
    parse_note_file,
    render_note_file,
    resolve_destination,
    title_slug,
)


def test_title_slug_kebab_and_truncate() -> None:
    assert title_slug("Hello, World!") == "hello-world"
    assert title_slug("") == ""
    long = "a" * 200
    out = title_slug(long)
    assert len(out) <= 60


def test_folder_for_scope_maps_each_prefix() -> None:
    assert folder_for_scope("topic:kotlin") == "topics/kotlin"
    assert folder_for_scope("project:personal-stack") == "projects/personal-stack"
    assert folder_for_scope("agent:_shared") == "agents/_shared"
    assert folder_for_scope("agent:claude") == "agents/claude"


def test_folder_for_scope_rejects_unsupported() -> None:
    with pytest.raises(ValueError, match="Unsupported scope"):
        folder_for_scope("personal")


def test_parse_note_file_round_trips_fields(tmp_path: Path) -> None:
    p = tmp_path / "note.md"
    p.write_text(
        "---\n"
        "id: 01HXYZ00000000000000000000\n"
        "type: lesson\n"
        "scope: _inbox\n"
        "source: claude-code\n"
        "captured_at: 2026-05-13T12:00:00+00:00\n"
        "session_id: sess-1\n"
        "confidence: 0.4\n"
        "tags: [k3s, vault]\n"
        "---\n"
        "\n"
        "# Some Title\n"
        "\n"
        "body line\n",
        encoding="utf-8",
    )
    front = parse_note_file(p)
    assert front.id == "01HXYZ00000000000000000000"
    assert front.type == "lesson"
    assert front.scope == "_inbox"
    assert front.session_id == "sess-1"
    assert front.confidence == 0.4
    assert front.tags == ("k3s", "vault")
    assert front.title == "Some Title"
    assert "body line" in front.body


def test_render_note_replaces_h1_and_writes_new_frontmatter(tmp_path: Path) -> None:
    p = tmp_path / "note.md"
    p.write_text(
        "---\n"
        "id: 01HXYZ\nsource: cc\ncaptured_at: 2026-05-13T12:00:00+00:00\n"
        "type: lesson\nscope: _inbox\nconfidence: 0.4\n"
        "---\n"
        "\n# Old title\n\nbody\n",
        encoding="utf-8",
    )
    front = parse_note_file(p)
    rendered = render_note_file(
        front=front,
        new_title="Clean Title",
        new_scope="topic:vault",
        new_type="lesson",
        new_tags=("ssh", "vault"),
        new_confidence=0.85,
        supersedes=("01HABC",),
        see_also=(),
    )
    assert "scope: topic:vault" in rendered
    assert "confidence: 0.85" in rendered
    assert "supersedes: [01HABC]" in rendered
    assert "tags: [ssh, vault]" in rendered
    # First H1 reflects the new title; the old H1 is gone.
    assert "# Clean Title" in rendered
    assert "# Old title" not in rendered


def test_resolve_destination_handles_collisions(tmp_path: Path) -> None:
    folder = "topics/kotlin"
    (tmp_path / folder / "lesson").mkdir(parents=True)
    (tmp_path / folder / "lesson" / "foo.md").write_text("x", encoding="utf-8")
    dest = resolve_destination(tmp_path, folder, "lesson", "Foo", "01A")
    assert dest.name == "foo-2.md"
    # Even the collision suffix gets a fresh slot.
    (tmp_path / folder / "lesson" / "foo-2.md").write_text("x", encoding="utf-8")
    dest = resolve_destination(tmp_path, folder, "lesson", "Foo", "01A")
    assert dest.name == "foo-3.md"


def _bare_remote(tmp_path: Path) -> Path:
    bare = tmp_path / "remote.git"
    Repo.init(bare, bare=True, initial_branch="main")
    seed = tmp_path / "seed"
    seed_repo = Repo.clone_from(bare, seed)
    seed_repo.config_writer().set_value("user", "email", "seed@test").release()
    seed_repo.config_writer().set_value("user", "name", "seed").release()
    (seed / "_inbox" / "2026-05-13").mkdir(parents=True)
    (seed / "_inbox" / "2026-05-13" / "120000-foo--01HXYZ00.md").write_text(
        "---\nid: 01HXYZ00000000000000000000\ntype: lesson\nscope: _inbox\n"
        "source: claude-code\ncaptured_at: 2026-05-13T12:00:00+00:00\n"
        "confidence: 0.4\n---\n\n# foo\n\nbody\n",
        encoding="utf-8",
    )
    seed_repo.index.add(["_inbox/2026-05-13/120000-foo--01HXYZ00.md"])
    seed_repo.index.commit("init")
    seed_repo.remotes.origin.push(refspec="HEAD:main")
    return bare


@pytest.fixture()
def clone(tmp_path: Path) -> Path:
    remote = _bare_remote(tmp_path)
    return Path(Repo.clone_from(remote, tmp_path / "clone").working_dir)


def test_curator_vault_promote_moves_and_commits(clone: Path) -> None:
    vault = CuratorVault(
        clone_dir=clone,
        author=Actor("curator", "curator@test"),
        ssh_key_path=None,
        push=False,
    )
    src = "_inbox/2026-05-13/120000-foo--01HXYZ00.md"
    dst = "topics/vault/lesson/clean-title.md"
    body = "---\nid: 01HXYZ\n---\n\n# Clean Title\n\nbody\n"
    result = vault.promote(
        source_rel=src,
        destination_rel=dst,
        new_body=body,
        commit_subject="curator(topic:vault): promote clean-title",
    )
    assert (clone / dst).exists()
    assert not (clone / src).exists()
    repo = Repo(clone)
    assert repo.head.commit.message.strip() == "curator(topic:vault): promote clean-title"
    assert result.new_relative_path == dst


def test_curator_vault_move_to_needs_review(clone: Path) -> None:
    vault = CuratorVault(
        clone_dir=clone,
        author=Actor("curator", "curator@test"),
        ssh_key_path=None,
        push=False,
    )
    src = "_inbox/2026-05-13/120000-foo--01HXYZ00.md"
    result = vault.move_to_needs_review(source_rel=src, reason="too-ambiguous")
    assert result.new_relative_path.startswith("_inbox/_needs-review/")
    assert (clone / result.new_relative_path).exists()
    repo = Repo(clone)
    assert "review too-ambiguous" in repo.head.commit.message


def test_curator_vault_move_to_needs_review_is_noop_when_already_in_review(clone: Path) -> None:
    """Drain pass re-classification: file is already in needs-review,
    classification fails again, Promoter calls move_to_needs_review
    with a source that's identical to the computed destination. The
    legacy code crashed with ``git mv X X`` exit 128; the no-op
    guard returns the existing path unchanged.
    """

    vault = CuratorVault(
        clone_dir=clone,
        author=Actor("curator", "curator@test"),
        ssh_key_path=None,
        push=False,
    )
    review_rel = "_inbox/_needs-review/120000-foo--01HXYZ00.md"
    # Seed the file directly in _needs-review/ so source==dest.
    target = clone / review_rel
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text("---\nid: x\n---\n# X\n", encoding="utf-8")
    repo = Repo(clone)
    repo.index.add([review_rel])
    repo.index.commit(
        "seed review file", author=Actor("seed", "seed@test"), committer=Actor("seed", "seed@test")
    )
    head_before = repo.head.commit.hexsha

    result = vault.move_to_needs_review(source_rel=review_rel, reason="classify-failed:retry")

    assert result.new_relative_path == review_rel
    # No new commit — caller did not actually move anything.
    assert repo.head.commit.hexsha == head_before
    assert (clone / review_rel).exists()


def test_curator_vault_commit_paths_no_op_when_clean(clone: Path) -> None:
    vault = CuratorVault(
        clone_dir=clone,
        author=Actor("curator", "curator@test"),
        ssh_key_path=None,
        push=False,
    )
    result = vault.commit_paths(rels=[], subject="curator(index): regenerate")
    assert result is None


def test_curator_vault_commit_paths_writes_a_commit(clone: Path) -> None:
    vault = CuratorVault(
        clone_dir=clone,
        author=Actor("curator", "curator@test"),
        ssh_key_path=None,
        push=False,
    )
    (clone / "_index").mkdir(parents=True, exist_ok=True)
    (clone / "_index" / "recent.md").write_text("# recent\n", encoding="utf-8")
    result = vault.commit_paths(
        rels=["_index/recent.md"],
        subject="curator(index): regenerate 1 index file(s)",
    )
    assert result is not None
    repo = Repo(clone)
    assert "curator(index): regenerate" in repo.head.commit.message
