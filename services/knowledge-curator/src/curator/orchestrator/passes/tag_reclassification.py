"""Tag reclassification pass.

This pass is intentionally thin: the SQL and mutation semantics live
in :class:`curator.reclassify.TagReclassifier`, while the orchestrator
wrapper exposes the cheap `has_work` probe and records a compact pass
watermark for operator visibility.
"""

from __future__ import annotations

from typing import Any

import structlog

from curator.orchestrator.protocol import PassOutcome, PassState
from curator.reclassify import TagReclassifier, summarise_tag_outcomes

log = structlog.get_logger(__name__)


class TagReclassificationPass:
    """Re-settle `kb_note_tags` after tag admin mutations."""

    name = "tag_reclassification"

    def __init__(self, *, reclassifier: TagReclassifier) -> None:
        self._reclassifier = reclassifier

    def has_work(self, state: PassState) -> bool:
        del state
        return self._reclassifier.has_work()

    def run(self, state: PassState) -> PassOutcome:
        del state
        watermark = self._reclassifier.current_watermark()
        if watermark is None:
            return PassOutcome(status="no_work", notes_processed=0)

        outcomes = self._reclassifier.run_pass(watermark=watermark)
        counts = summarise_tag_outcomes(outcomes)
        log.info(
            "tag_reclassification.complete",
            audit_watermark=_serialise_watermark(watermark),
            **counts,
        )
        return PassOutcome(
            status="success" if outcomes else "no_work",
            notes_processed=len(outcomes),
            watermark_after={
                "audit_watermark": _serialise_watermark(watermark),
                **counts,
            },
        )


def _serialise_watermark(value: Any) -> str:
    isoformat = getattr(value, "isoformat", None)
    if callable(isoformat):
        return str(isoformat())
    return str(value)
