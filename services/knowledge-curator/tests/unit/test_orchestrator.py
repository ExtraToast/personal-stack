"""Orchestrator scaffolding tests.

PR A ships the runner + Pass protocol + state store but registers
zero passes. These tests exercise the runner's decision matrix
against in-memory `Pass` doubles so PR B / C can layer real
implementations on a known-good orchestrator.
"""

from __future__ import annotations

from dataclasses import dataclass

import pytest

from curator.orchestrator.protocol import PassOutcome, PassState
from curator.orchestrator.runner import run_tick
from curator.store import InMemoryCuratorStore


@dataclass
class _FakePass:
    """Test-only Pass. Records every call so assertions can verify
    `has_work` was probed exactly once per tick and `run` only when
    `has_work` returned True.
    """

    name: str
    has_work_return: bool
    run_outcome: PassOutcome
    raise_on_run: Exception | None = None
    has_work_calls: int = 0
    run_calls: int = 0
    last_state: PassState | None = None

    def has_work(self, state: PassState) -> bool:  # type: ignore[override]
        self.has_work_calls += 1
        self.last_state = state
        return self.has_work_return

    def run(self, state: PassState) -> PassOutcome:  # type: ignore[override]
        self.run_calls += 1
        if self.raise_on_run is not None:
            raise self.raise_on_run
        return self.run_outcome


def _pass_state(store: InMemoryCuratorStore, name: str) -> PassState:
    row = store.load_pass_state(name)
    return PassState(
        pass_name=row.pass_name,
        last_started_at=row.last_started_at,
        last_completed_at=row.last_completed_at,
        last_status=row.last_status,
        watermark=row.watermark,
        notes_processed=row.notes_processed,
    )


def test_run_tick_skips_pass_with_no_work() -> None:
    store = InMemoryCuratorStore()
    pass_ = _FakePass(
        name="inbox",
        has_work_return=False,
        run_outcome=PassOutcome(status="success"),
    )

    counts = run_tick(store=store, passes=[pass_])  # type: ignore[arg-type]

    assert counts == {"no_work": 1, "success": 0, "failed": 0}
    assert pass_.has_work_calls == 1
    assert pass_.run_calls == 0
    state = store.load_pass_state("inbox")
    assert state.last_status == "no_work"
    # No history row for a no-work skip — keeps the audit log lean.
    assert store.pass_history == []


def test_run_tick_runs_pass_with_work_and_persists_watermark() -> None:
    store = InMemoryCuratorStore()
    new_watermark = {"max_captured_at": "2026-05-21T03:14:00Z"}
    pass_ = _FakePass(
        name="inbox",
        has_work_return=True,
        run_outcome=PassOutcome(
            status="success",
            notes_processed=3,
            watermark_after=new_watermark,
        ),
    )

    counts = run_tick(store=store, passes=[pass_])  # type: ignore[arg-type]

    assert counts == {"no_work": 0, "success": 1, "failed": 0}
    assert pass_.run_calls == 1
    state = store.load_pass_state("inbox")
    assert state.last_status == "success"
    assert state.watermark == new_watermark
    assert state.notes_processed == 3
    assert len(store.pass_history) == 1
    history = store.pass_history[0]
    assert history["status"] == "success"
    assert history["notes_processed"] == 3
    assert history["watermark_after"] == new_watermark


def test_run_tick_records_failed_when_pass_raises() -> None:
    store = InMemoryCuratorStore()
    pass_ = _FakePass(
        name="inbox",
        has_work_return=True,
        run_outcome=PassOutcome(status="success"),  # never reached
        raise_on_run=RuntimeError("boom"),
    )

    counts = run_tick(store=store, passes=[pass_])  # type: ignore[arg-type]

    assert counts == {"no_work": 0, "success": 0, "failed": 1}
    state = store.load_pass_state("inbox")
    assert state.last_status == "failed"
    # Watermark stays at the prior value so the next tick re-tries
    # the same candidates.
    assert state.watermark == {}
    assert len(store.pass_history) == 1
    assert store.pass_history[0]["status"] == "failed"
    assert "RuntimeError: boom" in store.pass_history[0]["error"]


def test_run_tick_processes_multiple_passes_independently() -> None:
    store = InMemoryCuratorStore()
    a = _FakePass(
        name="inbox",
        has_work_return=True,
        run_outcome=PassOutcome(status="success", notes_processed=1),
    )
    b = _FakePass(
        name="title_quality",
        has_work_return=False,
        run_outcome=PassOutcome(status="success"),
    )
    c = _FakePass(
        name="relation_enrichment",
        has_work_return=True,
        run_outcome=PassOutcome(status="success", notes_processed=2),
        raise_on_run=RuntimeError("transient"),
    )

    counts = run_tick(store=store, passes=[a, b, c])  # type: ignore[arg-type]

    assert counts == {"no_work": 1, "success": 1, "failed": 1}
    assert a.run_calls == 1
    assert b.run_calls == 0
    assert c.run_calls == 1
    assert store.load_pass_state("inbox").last_status == "success"
    assert store.load_pass_state("title_quality").last_status == "no_work"
    assert store.load_pass_state("relation_enrichment").last_status == "failed"


def test_pass_state_carries_existing_watermark_into_has_work() -> None:
    store = InMemoryCuratorStore()
    store.record_pass_outcome(
        pass_name="inbox",
        status="success",
        notes_processed=7,
        watermark_before={},
        watermark_after={"max_captured_at": "2026-05-20T20:00:00Z"},
        duration_seconds=1.5,
    )
    pass_ = _FakePass(
        name="inbox",
        has_work_return=False,
        run_outcome=PassOutcome(status="success"),
    )

    run_tick(store=store, passes=[pass_])  # type: ignore[arg-type]

    assert pass_.last_state is not None
    assert pass_.last_state.watermark == {"max_captured_at": "2026-05-20T20:00:00Z"}


def test_record_pass_outcome_rejects_invalid_status() -> None:
    store = InMemoryCuratorStore()
    with pytest.raises(ValueError, match="invalid status"):
        store.record_pass_outcome(
            pass_name="x",
            status="bogus",
            notes_processed=0,
            watermark_before={},
            watermark_after={},
            duration_seconds=0.1,
        )


def test_load_pass_state_synthesises_fresh_row_for_unknown_pass() -> None:
    store = InMemoryCuratorStore()
    state = store.load_pass_state("never_seen")
    assert state.last_status == "never_run"
    assert state.last_completed_at is None
    assert state.watermark == {}
