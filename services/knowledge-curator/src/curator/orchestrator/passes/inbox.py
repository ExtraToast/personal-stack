"""Inbox pass — promote new captures from ``_inbox/<YYYY-MM-DD>/``.

Drop-in replacement for the curator's legacy ``__main__`` entrypoint
on the inbox half: walks `_inbox/<day>/*.md` (skipping
``_needs-review/``), feeds each file through the existing
:class:`curator.promote.Promoter`, and regenerates the on-disk
indexes (`_index/recent.md`, per-topic MoCs, `_index/conflicts.md`)
when at least one note promoted.

The pass owns no watermark beyond "did any file actually move?".
The filesystem itself is the watermark — every promotion `git mv`s
the source file out of `_inbox/`, so an empty inbox directly
implies "no work". That's the contract :meth:`has_work` enforces.
"""

from __future__ import annotations

from collections.abc import Callable
from pathlib import Path

import structlog

from curator.orchestrator.protocol import PassOutcome, PassState
from curator.promote import Promoter
from curator.store import CuratorStore
from curator.vault import CuratorVault

log = structlog.get_logger(__name__)

INBOX_DIRNAME = "_inbox"
NEEDS_REVIEW_PREFIX = f"{INBOX_DIRNAME}/_needs-review/"


def _list_inbox(inbox_root: Path) -> list[str]:
    """List every promotable `.md` file under ``_inbox/<day>/``.

    Returns vault-relative paths so the Promoter sees the same shape
    the legacy entrypoint passed. Skips ``_inbox/_needs-review/`` —
    those files are owned by :class:`NeedsReviewDrainPass`.
    """

    if not inbox_root.exists():
        return []
    out: list[str] = []
    for path in sorted(inbox_root.rglob("*.md")):
        rel = path.relative_to(inbox_root.parent).as_posix()
        if rel.startswith(NEEDS_REVIEW_PREFIX):
            continue
        out.append(rel)
    return out


class InboxPass:
    """Promote fresh captures. ``name = 'inbox'``."""

    name = "inbox"

    def __init__(
        self,
        *,
        vault_clone_dir: Path,
        promoter: Promoter,
        vault: CuratorVault,
        store: CuratorStore,
        regenerate_indexes: Callable[[], None],
    ) -> None:
        self._vault_clone_dir = vault_clone_dir
        self._promoter = promoter
        self._vault = vault
        self._store = store
        self._regenerate_indexes = regenerate_indexes

    def has_work(self, state: PassState) -> bool:
        # Cheapest possible probe: short-circuit on the first
        # promotable `.md` file we see. The filesystem itself is the
        # watermark — successful promotions move files out, so an
        # empty inbox implies no work.
        inbox_root = self._vault_clone_dir / INBOX_DIRNAME
        if not inbox_root.exists():
            return False
        for path in inbox_root.rglob("*.md"):
            rel = path.relative_to(self._vault_clone_dir).as_posix()
            if not rel.startswith(NEEDS_REVIEW_PREFIX):
                return True
        return False

    def run(self, state: PassState) -> PassOutcome:
        # Pull + rebase so concurrent captures from the ingest-worker
        # are visible before we start moving files. Idempotent — no-op
        # when the working tree is already current.
        self._vault.sync()

        inbox_root = self._vault_clone_dir / INBOX_DIRNAME
        candidates = _list_inbox(inbox_root)
        log.info("inbox.pass_start", candidates=len(candidates))
        if not candidates:
            # Filesystem changed between has_work and run; rare race
            # (a concurrent drain pass promoted the only inbox file).
            return PassOutcome(status="no_work", notes_processed=0)

        promoted_count = 0
        for rel in candidates:
            outcome = self._promoter.promote_inbox_file(rel)
            log.info(
                "inbox.outcome",
                note_id=outcome.note_id,
                status=outcome.status,
                destination=outcome.destination_rel,
                reason=outcome.reason,
            )
            if outcome.status == "promoted":
                promoted_count += 1

        if promoted_count > 0:
            self._regenerate_indexes()

        # No persistent watermark — empty inbox + has_work=False is
        # the steady state. The orchestrator records last_completed_at
        # which is plenty for the audit log.
        return PassOutcome(
            status="success",
            notes_processed=len(candidates),
            watermark_after={},
        )
