"""Unit tests for the title-quality maintenance pass."""

from __future__ import annotations

from pathlib import Path

import httpx
import pytest
import respx
from git import Actor, Repo

from curator.maintenance.title_quality import (
    DEFAULT_PATTERNS,
    OllamaTitleRewriter,
    TitleRewriteError,
    run_title_quality,
)
from curator.store import InMemoryCuratorStore
from curator.vault import CuratorVault, NoteFrontmatter, render_note_file


def _ollama_response(title: str) -> dict:
    return {
        "choices": [
            {
                "message": {
                    "content": '{"title": "' + title + '"}',
                }
            }
        ]
    }


def _frontmatter(title: str = "How to bootstrap k3s") -> NoteFrontmatter:
    return NoteFrontmatter(
        id="kb_old01",
        type="note",
        scope="topic:kubernetes",
        source="agent",
        captured_at="2026-05-19T08:00:00Z",
        session_id=None,
        confidence=0.75,
        title=title,
        tags=("k3s", "bootstrap"),
        body="""# How to bootstrap k3s

k3s-init on the first node, then `k3s agent --server` on workers.
""",
        other={},
    )


def _seed_vault(clone_dir: Path, rel: str, title: str) -> None:
    target = clone_dir / rel
    target.parent.mkdir(parents=True, exist_ok=True)
    text = render_note_file(
        front=_frontmatter(title),
        new_title=title,
        new_scope="topic:kubernetes",
        new_type="note",
        new_tags=("k3s", "bootstrap"),
        new_confidence=0.75,
    )
    target.write_text(text, encoding="utf-8")


def _make_vault(tmp_path: Path) -> CuratorVault:
    clone_dir = tmp_path / "vault"
    clone_dir.mkdir()
    repo = Repo.init(clone_dir)
    # Initial commit so HEAD exists before commit_paths runs.
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


def test_default_patterns_match_known_bad_titles() -> None:
    import re

    combined = re.compile("|".join(DEFAULT_PATTERNS), re.IGNORECASE)
    bad = [
        "How to bootstrap k3s",
        "How does Vault Raft unseal work?",
        "Why HikariCP times out under load",
        "What is a CRaC checkpoint?",
        "On scaling a single-replica auth-api",
        "Notes on flannel-over-tailscale",
        "Introduction to LightRAG",
        "Guide to NixOS hosts",
        "Overview of the curator pipeline",
        "Did the upgrade work?",
    ]
    for title in bad:
        assert combined.search(title), title

    good = [
        "Vault Raft unseal requires Shamir keys",
        "HikariCP times out at default 30s leak threshold",
        "CRaC checkpoint replay reuses booted classloader",
        "LightRAG mix-mode runs extraction + generation",
    ]
    for title in good:
        assert not combined.search(title), title


def test_run_title_quality_rewrites_and_commits(tmp_path: Path) -> None:
    store = InMemoryCuratorStore(existing={"kb_old01"})
    store.title_quality_queue.append(
        (
            "kb_old01",
            "How to bootstrap k3s",
            "k3s-init on the first node, then `k3s agent --server` on workers.",
            "topics/kubernetes/note/k3s-bootstrap.md",
        )
    )
    vault = _make_vault(tmp_path)
    _seed_vault(
        vault._clone_dir if hasattr(vault, "_clone_dir") else tmp_path / "vault",
        "topics/kubernetes/note/k3s-bootstrap.md",
        "How to bootstrap k3s",
    )

    base_url = "http://ollama-heavy.test/v1"
    with respx.mock(base_url=base_url) as mock_router:
        mock_router.post("/chat/completions").respond(
            200,
            json=_ollama_response("k3s bootstrap pairs init with agent --server"),
        )
        with httpx.Client(timeout=10.0) as http:
            rewriter = OllamaTitleRewriter(
                base_url=base_url,
                model="qwen3:32b",
                timeout_seconds=10.0,
                client=http,
            )
            stats = run_title_quality(
                store=store,
                vault=vault,
                rewriter=rewriter,
                vault_clone_dir=tmp_path / "vault",
                patterns=list(DEFAULT_PATTERNS),
                batch_size=10,
            )

    assert stats.candidates == 1
    assert stats.rewritten == 1
    assert stats.unchanged == 0
    assert stats.skipped == 0
    assert store.titles["kb_old01"] == "k3s bootstrap pairs init with agent --server"
    updated = (tmp_path / "vault" / "topics/kubernetes/note/k3s-bootstrap.md").read_text()
    assert "# k3s bootstrap pairs init with agent --server" in updated
    assert "# How to bootstrap k3s" not in updated


def test_run_title_quality_skips_when_rewriter_returns_same_title(tmp_path: Path) -> None:
    store = InMemoryCuratorStore(existing={"kb_old02"})
    store.title_quality_queue.append(
        (
            "kb_old02",
            "How to bootstrap k3s",
            "body",
            "topics/kubernetes/note/k3s-bootstrap.md",
        )
    )
    vault = _make_vault(tmp_path)
    _seed_vault(
        tmp_path / "vault", "topics/kubernetes/note/k3s-bootstrap.md", "How to bootstrap k3s"
    )

    base_url = "http://ollama-heavy.test/v1"
    with respx.mock(base_url=base_url) as mock_router:
        mock_router.post("/chat/completions").respond(
            200, json=_ollama_response("How to bootstrap k3s")
        )
        with httpx.Client(timeout=10.0) as http:
            rewriter = OllamaTitleRewriter(
                base_url=base_url,
                model="qwen3:32b",
                timeout_seconds=10.0,
                client=http,
            )
            stats = run_title_quality(
                store=store,
                vault=vault,
                rewriter=rewriter,
                vault_clone_dir=tmp_path / "vault",
                patterns=list(DEFAULT_PATTERNS),
                batch_size=10,
            )

    assert stats.unchanged == 1
    assert stats.rewritten == 0
    assert "kb_old02" not in store.titles


def test_ollama_rewriter_raises_on_transport_error() -> None:
    base_url = "http://ollama-heavy.test/v1"
    with respx.mock(base_url=base_url) as mock_router:
        mock_router.post("/chat/completions").mock(side_effect=httpx.ConnectError("nope"))
        with httpx.Client(timeout=1.0) as http:
            rewriter = OllamaTitleRewriter(
                base_url=base_url,
                model="qwen3:32b",
                timeout_seconds=1.0,
                client=http,
            )
            with pytest.raises(TitleRewriteError):
                rewriter.rewrite(title="How to bootstrap k3s", body="x")
