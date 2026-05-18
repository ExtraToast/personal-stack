"""Git operations the curator performs on the vault clone.

The curator is the *second* writer to the working tree (after the
ingest-worker that drops files into ``_inbox/``). To stay safe with
LiveSync and human-Obsidian edits running through the same git
remote, every promote pass:

1. pulls + rebases first,
2. moves files inside the working tree with ``git mv`` so renames
   propagate cleanly,
3. rewrites the moved file's frontmatter (id + captured_at preserved;
   title / scope / type / tags / confidence updated from the
   classifier output),
4. stages + commits + pushes immediately.

The commit message uses the prefix ``curator(<scope>): promote
<slug>`` so ``git log --grep '^curator('`` filters curator commits
out of the larger history mix.
"""

from __future__ import annotations

import os
import re
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

import structlog
from git import Actor, Repo

# Pulled from the same helpers the worker uses so the slug shape is
# byte-identical across services. The worker is a sibling package in
# the same monorepo so the import path is short.
TITLE_SLUG_MAX_CHARS = 60


def title_slug(title: str) -> str:
    cleaned = re.sub(r"[^a-z0-9]+", "-", title.lower()).strip("-")
    if len(cleaned) <= TITLE_SLUG_MAX_CHARS:
        return cleaned
    truncated = cleaned[:TITLE_SLUG_MAX_CHARS]
    last_dash = truncated.rfind("-")
    if last_dash >= TITLE_SLUG_MAX_CHARS // 2:
        return truncated[:last_dash]
    return truncated


@dataclass(frozen=True, slots=True)
class PromotionResult:
    new_relative_path: str
    commit_sha: str


@dataclass(frozen=True, slots=True)
class NoteFrontmatter:
    """Parsed frontmatter — only the fields the curator cares about.

    The curator rewrites these on promote; everything else (`source`,
    `captured_at`, `id`, `session_id`) is preserved verbatim.
    """

    id: str
    type: str
    scope: str
    source: str
    captured_at: str
    session_id: str | None
    confidence: float
    title: str
    tags: tuple[str, ...]
    body: str
    other: dict[str, str]


_FRONTMATTER_RE = re.compile(r"^---\n(.*?)\n---\n(.*)$", re.DOTALL)
_H1_RE = re.compile(r"^#\s+(.+)$", re.MULTILINE)
_TAG_LIST_RE = re.compile(r"^\[(.*)\]$")


def parse_note_file(path: Path) -> NoteFrontmatter:
    raw = path.read_text(encoding="utf-8")
    match = _FRONTMATTER_RE.search(raw)
    if not match:
        raise ValueError(f"{path}: no frontmatter block")
    meta_raw, body = match.group(1), match.group(2)
    meta: dict[str, str] = {}
    for line in meta_raw.splitlines():
        if ":" not in line:
            continue
        key, _, value = line.partition(":")
        meta[key.strip()] = value.strip()

    tags_raw = meta.pop("tags", "")
    tags: tuple[str, ...] = ()
    if tags_raw:
        tag_match = _TAG_LIST_RE.match(tags_raw)
        if tag_match:
            tags = tuple(t.strip() for t in tag_match.group(1).split(",") if t.strip())

    confidence_raw = meta.pop("confidence", "0.4")
    try:
        confidence = float(confidence_raw)
    except ValueError:
        confidence = 0.4

    h1 = _H1_RE.search(body)
    title = (h1.group(1).strip() if h1 else "").strip()

    return NoteFrontmatter(
        id=meta.pop("id", ""),
        type=meta.pop("type", "note"),
        scope=meta.pop("scope", "_inbox"),
        source=meta.pop("source", "manual"),
        captured_at=meta.pop("captured_at", datetime.now().isoformat()),
        session_id=meta.pop("session_id", None) or None,
        confidence=confidence,
        title=title,
        tags=tags,
        body=body,
        other=meta,
    )


def render_note_file(
    *,
    front: NoteFrontmatter,
    new_title: str,
    new_scope: str,
    new_type: str,
    new_tags: tuple[str, ...],
    new_confidence: float,
    supersedes: tuple[str, ...] = (),
    see_also: tuple[str, ...] = (),
) -> str:
    """Render the on-disk markdown after a successful promotion."""

    lines = [
        "---",
        f"id: {front.id}",
        f"type: {new_type}",
        f"scope: {new_scope}",
        f"source: {front.source}",
        f"captured_at: {front.captured_at}",
    ]
    if front.session_id:
        lines.append(f"session_id: {front.session_id}")
    lines.append(f"confidence: {new_confidence}")
    if supersedes:
        lines.append(f"supersedes: [{', '.join(supersedes)}]")
    if see_also:
        lines.append(f"see_also: [{', '.join(see_also)}]")
    if new_tags:
        lines.append(f"tags: [{', '.join(sorted(new_tags))}]")
    lines.append("---")
    lines.append("")
    lines.append(f"# {new_title}")
    lines.append("")

    body = front.body.strip()
    # Strip the leading `# old title` line so we don't end up with
    # two H1 headers; the rest of the body stays verbatim.
    h1 = _H1_RE.search(body)
    if h1 and body.lstrip().startswith("#"):
        body = body[h1.end() :].lstrip("\n")
    lines.append(body)
    lines.append("")
    return "\n".join(lines)


def folder_for_scope(scope: str) -> str:
    """Translate a `scope` value into its on-disk folder.

    Examples:
        topic:kotlin         -> topics/kotlin
        project:foo-bar      -> projects/foo-bar
        agent:_shared        -> agents/_shared
        agent:claude         -> agents/claude
    """

    if scope.startswith("topic:"):
        return f"topics/{scope.split(':', 1)[1]}"
    if scope.startswith("project:"):
        return f"projects/{scope.split(':', 1)[1]}"
    if scope.startswith("agent:"):
        return f"agents/{scope.split(':', 1)[1]}"
    raise ValueError(f"Unsupported scope for promotion: {scope!r}")


def resolve_destination(
    clone_dir: Path,
    folder: str,
    note_type: str,
    title: str,
    note_id: str,
) -> Path:
    """Decide the final path, with collision suffixes.

    Files live at `<folder>/<type>/<slug>.md`. Collisions add
    `-2`, `-3`, etc.; never overwrite. As a last resort (slug empty)
    fall back to an id-derived stub.
    """

    base_slug = title_slug(title) or note_id[:8].lower() or "untitled"
    target_dir = clone_dir / folder / note_type
    target_dir.mkdir(parents=True, exist_ok=True)
    candidate = target_dir / f"{base_slug}.md"
    suffix = 2
    while candidate.exists():
        candidate = target_dir / f"{base_slug}-{suffix}.md"
        suffix += 1
    return candidate


class CuratorVault:
    """Wraps the git working tree the curator promotes notes through."""

    def __init__(
        self,
        *,
        clone_dir: Path,
        author: Actor,
        ssh_key_path: str | None,
        push: bool = True,
    ) -> None:
        self._clone_dir = clone_dir
        self._repo = Repo(clone_dir)
        self._author = author
        self._ssh_key_path = ssh_key_path
        self._push = push
        self._log = structlog.get_logger(__name__)

    def sync(self) -> None:
        """Pull --rebase --autostash to absorb concurrent writes."""

        with self._repo.git.custom_environment(**self._git_env()):
            self._repo.remotes.origin.pull(rebase=True, autostash=True)

    def promote(
        self,
        *,
        source_rel: str,
        destination_rel: str,
        new_body: str,
        commit_subject: str,
    ) -> PromotionResult:
        src = self._clone_dir / source_rel
        dst = self._clone_dir / destination_rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        # Use `git mv` so the rename is tracked, then overwrite the
        # moved file's content with the rewritten frontmatter + body.
        self._repo.git.mv(source_rel, destination_rel)
        dst.write_text(new_body, encoding="utf-8")
        self._repo.index.add([destination_rel])
        commit = self._repo.index.commit(
            commit_subject, author=self._author, committer=self._author
        )
        if self._push:
            with self._repo.git.custom_environment(**self._git_env()):
                self._repo.remotes.origin.push()
        self._log.info(
            "curator.promoted",
            source=source_rel,
            destination=destination_rel,
            sha=commit.hexsha[:12],
        )
        # Silence unused-name warning for static analyzers; the path
        # is what we return downstream, but local file existence is
        # the post-condition we ALSO assert in tests.
        _ = src
        return PromotionResult(new_relative_path=destination_rel, commit_sha=commit.hexsha)

    def move_to_needs_review(
        self,
        *,
        source_rel: str,
        reason: str,
    ) -> PromotionResult:
        """Move an unclassifiable note to `_inbox/_needs-review/`.

        We commit + push so the human looking at the vault sees the
        flagged set without having to run `git status` on the cluster
        clone. The commit subject carries the reason so `git log`
        captures the audit trail.
        """

        src = self._clone_dir / source_rel
        review_rel = f"_inbox/_needs-review/{src.name}"
        review_path = self._clone_dir / review_rel
        review_path.parent.mkdir(parents=True, exist_ok=True)
        self._repo.git.mv(source_rel, review_rel)
        commit = self._repo.index.commit(
            f"curator: review {reason}",
            author=self._author,
            committer=self._author,
        )
        if self._push:
            with self._repo.git.custom_environment(**self._git_env()):
                self._repo.remotes.origin.push()
        return PromotionResult(new_relative_path=review_rel, commit_sha=commit.hexsha)

    def _git_env(self) -> dict[str, str]:
        env = dict(os.environ)
        if self._ssh_key_path:
            env["GIT_SSH_COMMAND"] = (
                f"ssh -i {self._ssh_key_path} -o IdentitiesOnly=yes "
                f"-o StrictHostKeyChecking=accept-new"
            )
        return env
