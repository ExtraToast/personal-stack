"""Needs-review drain — re-classify stuck inbox files.

When the curator's :class:`curator.promote.Promoter` can not classify
or validate a fresh capture, it moves the file to
``_inbox/_needs-review/`` and tags the frontmatter with the failure
reason. The drain pass re-feeds each such file through the same
pipeline: model + recall context have moved on since the last
attempt, so a re-run with the heavy qwen3:32b model promotes most
of them.

Watermark: ``state.last_completed_at``. The drain re-evaluates only
files whose mtime advanced past the last successful drain — that
covers both fresh moves into ``_needs-review/`` (the inbox pass
just gave up on them) and human edits to existing review files.
"""

from __future__ import annotations

from datetime import UTC, datetime
from pathlib import Path

import structlog

from curator.orchestrator.protocol import PassOutcome, PassState
from curator.promote import Promoter
from curator.vault import CuratorVault

log = structlog.get_logger(__name__)

INBOX_DIRNAME = "_inbox"
NEEDS_REVIEW_DIRNAME = "_needs-review"


def _list_needs_review(review_root: Path, since: datetime | None) -> list[str]:
    """List ``.md`` files directly under ``_inbox/_needs-review/``
    whose mtime is strictly newer than ``since``.

    ``since=None`` returns every file. Paths are vault-relative so
    the Promoter sees the same shape it gets for fresh inbox files.
    """

    if not review_root.exists():
        return []
    threshold = since.timestamp() if since is not None else None
    out: list[str] = []
    for path in sorted(review_root.glob("*.md")):
        if threshold is not None and path.stat().st_mtime <= threshold:
            continue
        rel = path.relative_to(review_root.parent.parent).as_posix()
        out.append(rel)
    return out


def _has_review_file_newer_than(review_root: Path, since: datetime | None) -> bool:
    if not review_root.exists():
        return False
    threshold = since.timestamp() if since is not None else None
    for path in review_root.glob("*.md"):
        if threshold is None or path.stat().st_mtime > threshold:
            return True
    return False


class NeedsReviewDrainPass:
    """Re-classify ``_inbox/_needs-review/`` files. ``name = 'needs_review_drain'``."""

    name = "needs_review_drain"

    def __init__(
        self,
        *,
        vault_clone_dir: Path,
        promoter: Promoter,
        vault: CuratorVault,
    ) -> None:
        self._vault_clone_dir = vault_clone_dir
        self._promoter = promoter
        self._vault = vault

    def has_work(self, state: PassState) -> bool:
        review_root = self._vault_clone_dir / INBOX_DIRNAME / NEEDS_REVIEW_DIRNAME
        return _has_review_file_newer_than(review_root, state.last_completed_at)

    def run(self, state: PassState) -> PassOutcome:
        # Sync first so a file the inbox pass just moved into review
        # this very tick is visible.
        self._vault.sync()

        review_root = self._vault_clone_dir / INBOX_DIRNAME / NEEDS_REVIEW_DIRNAME
        candidates = _list_needs_review(review_root, state.last_completed_at)
        log.info(
            "needs_review_drain.pass_start",
            candidates=len(candidates),
            since=state.last_completed_at,
        )
        if not candidates:
            return PassOutcome(status="no_work", notes_processed=0)

        for rel in candidates:
            outcome = self._promoter.promote_inbox_file(rel)
            log.info(
                "needs_review_drain.outcome",
                note_id=outcome.note_id,
                status=outcome.status,
                destination=outcome.destination_rel,
                reason=outcome.reason,
            )

        # last_completed_at advances on each successful run via the
        # store, so the next has_work call sees only files modified
        # after this tick. No JSONB watermark needed for this pass.
        # Stamp the watermark with the cut-off ISO timestamp anyway —
        # makes the audit log self-documenting.
        return PassOutcome(
            status="success",
            notes_processed=len(candidates),
            watermark_after={
                "since": (state.last_completed_at.isoformat() if state.last_completed_at else None),
                "cutoff": datetime.now(tz=UTC).isoformat(),
            },
        )
