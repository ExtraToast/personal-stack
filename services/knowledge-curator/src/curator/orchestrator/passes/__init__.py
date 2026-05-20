"""Concrete :class:`curator.orchestrator.protocol.Pass` implementations.

Each module exports a single Pass class. Adding a new pass = new
module here + register it in
:func:`curator.orchestrator.runner.build_passes`.
"""

from curator.orchestrator.passes.inbox import InboxPass
from curator.orchestrator.passes.needs_review_drain import NeedsReviewDrainPass

__all__ = ["InboxPass", "NeedsReviewDrainPass"]
