"""Event/state-driven curator orchestration.

The orchestrator replaces the old "every 5 minutes, do everything"
CronJob shape with a single tick that asks each registered Pass
``has_work(state)`` and only runs the ones with new candidates since
their last successful tick. State lives in Postgres (`kb_curator_runs`)
and is keyed by `pass.name`; each pass owns the shape of its own
JSONB watermark.

Public surface:

- :class:`curator.orchestrator.protocol.Pass`,
  :class:`PassState`, :class:`PassOutcome` — what a maintenance pass
  must implement.
- :func:`curator.orchestrator.runner.main` — the CronJob entrypoint.

The package ships **without any passes registered**. PR B + C wire
the actual passes (inbox, needs-review drain, title quality,
relation enrichment) on top of the same scaffolding.
"""

from curator.orchestrator.protocol import (
    Pass,
    PassOutcome,
    PassState,
)
from curator.orchestrator.runner import main

__all__ = ["Pass", "PassOutcome", "PassState", "main"]
