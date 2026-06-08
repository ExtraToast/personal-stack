"""Unit tests for the inbox + needs-review-drain Pass implementations."""

from __future__ import annotations

import os
import time
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from pathlib import Path

from curator.orchestrator.passes.inbox import InboxPass
from curator.orchestrator.passes.needs_review_drain import NeedsReviewDrainPass
from curator.orchestrator.protocol import PassState
from curator.store import InMemoryCuratorStore


@dataclass(slots=True)
class _StubOutcome:
    note_id: str = "kb_test"
    status: str = "promoted"
    destination_rel: str = "topics/test/note/x.md"
    reason: str | None = None


class _StubPromoter:
    """Records every promote call. Returns `_StubOutcome` so the
    real `Promoter` does not have to be wired in for unit tests.
    """

    def __init__(self, statuses: list[str] | None = None) -> None:
        self.calls: list[str] = []
        self._statuses = statuses or []

    def promote_inbox_file(self, rel: str) -> _StubOutcome:
        self.calls.append(rel)
        status = self._statuses.pop(0) if self._statuses else "promoted"
        return _StubOutcome(status=status, destination_rel=f"topics/test/note/{rel}")


class _StubVault:
    def __init__(self) -> None:
        self.sync_calls = 0

    def sync(self) -> None:
        self.sync_calls += 1


# -------- helpers ---------------------------------------------------


def _write_inbox_file(
    vault_dir: Path, rel: str, content: str = "x", *, mtime: float | None = None
) -> Path:
    full = vault_dir / rel
    full.parent.mkdir(parents=True, exist_ok=True)
    full.write_text(content, encoding="utf-8")
    if mtime is not None:
        os.utime(full, (mtime, mtime))
    return full


def _state(
    name: str, *, last_completed_at: datetime | None = None, watermark: dict | None = None
) -> PassState:
    return PassState(
        pass_name=name,
        last_started_at=None,
        last_completed_at=last_completed_at,
        last_status="never_run" if last_completed_at is None else "success",
        watermark=watermark or {},
        notes_processed=0,
    )


# -------- InboxPass ------------------------------------------------


def test_inbox_pass_has_work_returns_false_when_inbox_dir_missing(tmp_path: Path) -> None:
    pass_ = InboxPass(
        vault_clone_dir=tmp_path,
        promoter=_StubPromoter(),  # type: ignore[arg-type]
        vault=_StubVault(),  # type: ignore[arg-type]
        store=InMemoryCuratorStore(),
        regenerate_indexes=lambda: None,
    )
    assert pass_.has_work(_state("inbox")) is False


def test_inbox_pass_has_work_skips_needs_review(tmp_path: Path) -> None:
    _write_inbox_file(tmp_path, "_inbox/_needs-review/note.md")
    pass_ = InboxPass(
        vault_clone_dir=tmp_path,
        promoter=_StubPromoter(),  # type: ignore[arg-type]
        vault=_StubVault(),  # type: ignore[arg-type]
        store=InMemoryCuratorStore(),
        regenerate_indexes=lambda: None,
    )
    assert pass_.has_work(_state("inbox")) is False


def test_inbox_pass_has_work_skips_discarded(tmp_path: Path) -> None:
    _write_inbox_file(tmp_path, "_inbox/_discarded/note.md")
    pass_ = InboxPass(
        vault_clone_dir=tmp_path,
        promoter=_StubPromoter(),  # type: ignore[arg-type]
        vault=_StubVault(),  # type: ignore[arg-type]
        store=InMemoryCuratorStore(),
        regenerate_indexes=lambda: None,
    )
    assert pass_.has_work(_state("inbox")) is False


def test_inbox_pass_has_work_returns_true_when_promotable_file_present(tmp_path: Path) -> None:
    _write_inbox_file(tmp_path, "_inbox/2026-05-21/120000-note--abcdef01.md")
    pass_ = InboxPass(
        vault_clone_dir=tmp_path,
        promoter=_StubPromoter(),  # type: ignore[arg-type]
        vault=_StubVault(),  # type: ignore[arg-type]
        store=InMemoryCuratorStore(),
        regenerate_indexes=lambda: None,
    )
    assert pass_.has_work(_state("inbox")) is True


def test_inbox_pass_has_work_falls_back_to_db_when_filesystem_empty(tmp_path: Path) -> None:
    # Filesystem is empty but the DB has an _inbox note — the case
    # that caused the May-2026 stuck note (captured to kb_notes and
    # pushed to the remote but absent from the local vault clone).
    store = InMemoryCuratorStore()
    store.inbox_vault_paths = ["_inbox/2026-05-18/200949-some-note--abcdef01.md"]
    pass_ = InboxPass(
        vault_clone_dir=tmp_path,
        promoter=_StubPromoter(),  # type: ignore[arg-type]
        vault=_StubVault(),  # type: ignore[arg-type]
        store=store,
        regenerate_indexes=lambda: None,
    )
    assert pass_.has_work(_state("inbox")) is True


def test_inbox_pass_has_work_ignores_needs_review_paths_in_db_fallback(tmp_path: Path) -> None:
    # Only _needs-review paths in the DB — not work for the inbox pass;
    # those are owned by NeedsReviewDrainPass.
    store = InMemoryCuratorStore()
    store.inbox_vault_paths = ["_inbox/_needs-review/stale.md"]
    pass_ = InboxPass(
        vault_clone_dir=tmp_path,
        promoter=_StubPromoter(),  # type: ignore[arg-type]
        vault=_StubVault(),  # type: ignore[arg-type]
        store=store,
        regenerate_indexes=lambda: None,
    )
    assert pass_.has_work(_state("inbox")) is False


def test_inbox_pass_has_work_ignores_discarded_paths_in_db_fallback(tmp_path: Path) -> None:
    store = InMemoryCuratorStore()
    store.inbox_vault_paths = ["_inbox/_discarded/stale.md"]
    pass_ = InboxPass(
        vault_clone_dir=tmp_path,
        promoter=_StubPromoter(),  # type: ignore[arg-type]
        vault=_StubVault(),  # type: ignore[arg-type]
        store=store,
        regenerate_indexes=lambda: None,
    )
    assert pass_.has_work(_state("inbox")) is False


def test_inbox_pass_run_processes_files_and_calls_regenerate_when_promotions_happen(
    tmp_path: Path,
) -> None:
    _write_inbox_file(tmp_path, "_inbox/2026-05-21/120000-a--aaaaaaaa.md")
    _write_inbox_file(tmp_path, "_inbox/2026-05-21/130000-b--bbbbbbbb.md")
    promoter = _StubPromoter(statuses=["promoted", "needs_review"])
    vault = _StubVault()
    regen_calls = []

    pass_ = InboxPass(
        vault_clone_dir=tmp_path,
        promoter=promoter,  # type: ignore[arg-type]
        vault=vault,  # type: ignore[arg-type]
        store=InMemoryCuratorStore(),
        regenerate_indexes=lambda: regen_calls.append(True),
    )
    outcome = pass_.run(_state("inbox"))

    assert outcome.status == "success"
    assert outcome.notes_processed == 2
    # promote_inbox_file called once per discovered candidate.
    assert promoter.calls == [
        "_inbox/2026-05-21/120000-a--aaaaaaaa.md",
        "_inbox/2026-05-21/130000-b--bbbbbbbb.md",
    ]
    # Regenerate runs only when at least one note promoted.
    assert len(regen_calls) == 1
    assert vault.sync_calls == 1


def test_inbox_pass_run_skips_regenerate_when_nothing_promoted(tmp_path: Path) -> None:
    _write_inbox_file(tmp_path, "_inbox/2026-05-21/120000-a--aaaaaaaa.md")
    promoter = _StubPromoter(statuses=["needs_review"])
    regen_calls = []

    pass_ = InboxPass(
        vault_clone_dir=tmp_path,
        promoter=promoter,  # type: ignore[arg-type]
        vault=_StubVault(),  # type: ignore[arg-type]
        store=InMemoryCuratorStore(),
        regenerate_indexes=lambda: regen_calls.append(True),
    )
    outcome = pass_.run(_state("inbox"))

    assert outcome.status == "success"
    assert outcome.notes_processed == 1
    assert regen_calls == []


def test_inbox_pass_run_returns_no_work_on_race_emptied_inbox(tmp_path: Path) -> None:
    (tmp_path / "_inbox" / "2026-05-21").mkdir(parents=True)
    pass_ = InboxPass(
        vault_clone_dir=tmp_path,
        promoter=_StubPromoter(),  # type: ignore[arg-type]
        vault=_StubVault(),  # type: ignore[arg-type]
        store=InMemoryCuratorStore(),
        regenerate_indexes=lambda: None,
    )
    outcome = pass_.run(_state("inbox"))
    assert outcome.status == "no_work"


# -------- NeedsReviewDrainPass ------------------------------------


def test_drain_pass_has_work_returns_false_when_dir_missing(tmp_path: Path) -> None:
    pass_ = NeedsReviewDrainPass(
        vault_clone_dir=tmp_path,
        promoter=_StubPromoter(),  # type: ignore[arg-type]
        vault=_StubVault(),  # type: ignore[arg-type]
    )
    assert pass_.has_work(_state("needs_review_drain")) is False


def test_drain_pass_has_work_returns_true_on_first_run(tmp_path: Path) -> None:
    _write_inbox_file(tmp_path, "_inbox/_needs-review/old.md")
    pass_ = NeedsReviewDrainPass(
        vault_clone_dir=tmp_path,
        promoter=_StubPromoter(),  # type: ignore[arg-type]
        vault=_StubVault(),  # type: ignore[arg-type]
    )
    # Never-run state: any file = work.
    assert pass_.has_work(_state("needs_review_drain", last_completed_at=None)) is True


def test_drain_pass_has_work_false_when_no_file_modified_since_last_run(tmp_path: Path) -> None:
    # Stamp file in the past, watermark in the future.
    past = time.time() - 3600
    _write_inbox_file(tmp_path, "_inbox/_needs-review/stale.md", mtime=past)
    last_completed = datetime.now(tz=UTC)

    pass_ = NeedsReviewDrainPass(
        vault_clone_dir=tmp_path,
        promoter=_StubPromoter(),  # type: ignore[arg-type]
        vault=_StubVault(),  # type: ignore[arg-type]
    )
    assert pass_.has_work(_state("needs_review_drain", last_completed_at=last_completed)) is False


def test_drain_pass_has_work_true_when_file_touched_after_last_run(tmp_path: Path) -> None:
    last_completed = datetime.now(tz=UTC) - timedelta(hours=1)
    # File mtime newer than last_completed.
    _write_inbox_file(tmp_path, "_inbox/_needs-review/fresh.md", mtime=time.time())
    pass_ = NeedsReviewDrainPass(
        vault_clone_dir=tmp_path,
        promoter=_StubPromoter(),  # type: ignore[arg-type]
        vault=_StubVault(),  # type: ignore[arg-type]
    )
    assert pass_.has_work(_state("needs_review_drain", last_completed_at=last_completed)) is True


def test_drain_pass_run_processes_only_files_modified_after_last_run(tmp_path: Path) -> None:
    last_completed = datetime.now(tz=UTC) - timedelta(hours=2)
    _write_inbox_file(tmp_path, "_inbox/_needs-review/old.md", mtime=time.time() - 3 * 3600)
    _write_inbox_file(tmp_path, "_inbox/_needs-review/new.md", mtime=time.time())

    promoter = _StubPromoter(statuses=["promoted"])
    pass_ = NeedsReviewDrainPass(
        vault_clone_dir=tmp_path,
        promoter=promoter,  # type: ignore[arg-type]
        vault=_StubVault(),  # type: ignore[arg-type]
    )
    outcome = pass_.run(_state("needs_review_drain", last_completed_at=last_completed))

    assert outcome.status == "success"
    assert outcome.notes_processed == 1
    assert promoter.calls == ["_inbox/_needs-review/new.md"]


def test_drain_pass_run_returns_no_work_when_only_stale_files_left(tmp_path: Path) -> None:
    last_completed = datetime.now(tz=UTC)
    _write_inbox_file(tmp_path, "_inbox/_needs-review/old.md", mtime=time.time() - 3600)
    pass_ = NeedsReviewDrainPass(
        vault_clone_dir=tmp_path,
        promoter=_StubPromoter(),  # type: ignore[arg-type]
        vault=_StubVault(),  # type: ignore[arg-type]
    )
    outcome = pass_.run(_state("needs_review_drain", last_completed_at=last_completed))
    assert outcome.status == "no_work"
