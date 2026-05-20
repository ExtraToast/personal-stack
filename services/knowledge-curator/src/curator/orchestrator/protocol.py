"""Pass protocol + state / outcome dataclasses.

Each maintenance Pass implements :class:`Pass`. The orchestrator
loop walks the registered passes in order, asks each
``has_work(state)`` (a cheap SQL or filesystem probe), and only
calls ``run(state)`` for the ones that have pending candidates
since their last successful tick.

The watermark inside :class:`PassState` is intentionally an opaque
``dict[str, Any]`` — each Pass picks its own shape and the
orchestrator never reads inside it. That keeps adding a new Pass
to a single-file PR; no schema change required.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from typing import Any, Protocol


@dataclass(frozen=True, slots=True)
class PassState:
    """State loaded from `kb_curator_runs` for a single pass.

    `last_completed_at` is None on the very first run for a pass —
    `has_work` implementations should treat that as "process
    everything visible". `watermark` is the JSONB blob, parsed.
    """

    pass_name: str
    last_started_at: datetime | None
    last_completed_at: datetime | None
    last_status: str
    watermark: dict[str, Any] = field(default_factory=dict)
    notes_processed: int = 0


@dataclass(frozen=True, slots=True)
class PassOutcome:
    """What a Pass.run returns.

    `status` is one of `success` | `no_work` | `failed`. `no_work`
    is reserved for the edge case where `has_work` raced and saw a
    candidate that vanished by the time `run` opened its
    transaction. Most no-op ticks short-circuit before `run` is
    called at all.

    `watermark_after` becomes the new persisted state. If the Pass
    failed without making any progress, return the unchanged
    `state.watermark` so the next tick re-tries the same candidates.
    """

    status: str
    notes_processed: int = 0
    watermark_after: dict[str, Any] = field(default_factory=dict)
    error: str | None = None

    def summary(self) -> dict[str, Any]:
        out: dict[str, Any] = {
            "status": self.status,
            "notes_processed": self.notes_processed,
        }
        if self.error:
            out["error"] = self.error
        return out


class Pass(Protocol):
    """Maintenance pass interface.

    Implementations should be cheap to construct — the orchestrator
    builds them once per tick. Heavy resources (Ollama clients, vault
    repo, etc.) are injected by the runner, not owned by the Pass
    instance.
    """

    name: str

    def has_work(self, state: PassState) -> bool:
        """Return True if there is at least one new candidate since
        `state.last_completed_at` / `state.watermark`.

        Must be cheap — a single indexed SQL count or a `pathlib.stat`
        is the target. Slow `has_work` defeats the whole point of the
        orchestrator (the 98% no-op ticks).
        """
        ...

    def run(self, state: PassState) -> PassOutcome:
        """Process the pending candidates and return the new
        watermark.

        Re-entrant under the orchestrator's per-pass
        `pg_advisory_xact_lock(hashtext(pass_name))`: the runner
        holds the lock for the whole `run()` call so a slow pass
        can not overlap itself across two ticks.
        """
        ...
