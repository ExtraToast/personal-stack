"""Retroactive title renormaliser.

Background. The inbox classifier already emits short, claim-shaped
titles (the JSON schema caps them at 80 chars). Notes captured
before that contract landed in `kb_notes` still carry the old
prose-y titles — "A discussion of …", "Notes on …", "How to …".
This module is the one-shot fix: walk every unlocked row whose
title exceeds the budget, ask the classifier to propose a tighter
rewrite, and apply it when the rewrite itself meets the contract.

Loop:

    1. Fetch up to `MAX_PER_PASS` candidates from `kb_notes` where
       `title_locked = FALSE AND char_length(title) > 80`,
       newest-first so the most-recent offences clear quickest.
    2. For each, call `OllamaClassifier.classify(title=, body=)`
       and read back `Classification.title`. The classifier's
       output is already grammar-constrained to ≤80 chars; the
       contract check is belt-and-braces.
    3. Audit-write first (so a crash leaves the audit row but not
       a stale rename), then UPDATE kb_notes.title.

Out of scope here:

  - Renaming the vault markdown file on disk. The DB is the source
    of truth for displayed title; the vault filename stays at the
    capture-time slug and `git log` preserves history. A future PR
    can introduce a `git mv` pass once the title-rename rate
    settles.
  - LightRAG re-ingest on title change. Lands with the index
    regeneration pass.
"""

from __future__ import annotations

from collections.abc import Iterable
from dataclasses import dataclass
from typing import Any, Literal, Protocol

import structlog
from psycopg import sql

from curator.audit import AuditRecorder
from curator.classify import ClassificationError, OllamaClassifier

TITLE_CHAR_BUDGET = 80
TITLE_MIN_LENGTH = 4
MAX_PER_PASS = 20

_ACTOR = "kb-renormalise-titles"
_ACTION = "rename_title"
_TARGET_KIND = "kb_note"


_RenameStatus = Literal["renamed", "kept", "skipped", "failed"]


@dataclass(frozen=True, slots=True)
class RenameOutcome:
    """Per-row result of one renormalisation attempt.

    `renamed`  — title rewritten + audit row persisted.
    `kept`     — classifier returned the same title; nothing to do.
    `skipped`  — classifier returned a new title that still violates
                 the contract (length or trailing punctuation). The
                 original title stays in the DB; the row will be
                 reconsidered next pass.
    `failed`   — classifier raised or some other transient error.
                 The row stays unlocked and untouched.
    """

    note_id: str
    status: _RenameStatus
    before_title: str
    after_title: str
    reason: str = ""


class _Store(Protocol):
    def connection(self) -> Any: ...


def _meets_contract(title: str, budget: int) -> bool:
    if not (TITLE_MIN_LENGTH <= len(title) <= budget):
        return False
    if not title[0].isupper():
        return False
    return title[-1] not in ".?!"


class TitleRenormaliser:
    """Stateless title-rewriting pass. One instance per CronJob fire."""

    def __init__(
        self,
        *,
        store: _Store,
        classifier: OllamaClassifier,
        audit: AuditRecorder,
        char_budget: int = TITLE_CHAR_BUDGET,
        max_per_pass: int = MAX_PER_PASS,
    ) -> None:
        self._store = store
        self._classifier = classifier
        self._audit = audit
        self._budget = char_budget
        self._max_per_pass = max_per_pass
        self._log = structlog.get_logger(__name__)

    def run_pass(self) -> list[RenameOutcome]:
        """One renormalisation pass. Returns one outcome per candidate
        the pass touched; an empty list means no candidates qualified.
        """

        candidates = self._fetch_candidates()
        outcomes: list[RenameOutcome] = []
        for note_id, before_title, body, scope in candidates:
            outcomes.append(self._renormalise_one(note_id, before_title, body, scope))
        return outcomes

    def _fetch_candidates(self) -> list[tuple[str, str, str, str]]:
        with self._store.connection() as conn, conn.cursor() as cur:
            cur.execute(
                sql.SQL(
                    "SELECT id, title, body, scope FROM kb_notes "
                    "WHERE title_locked = FALSE AND char_length(title) > %s "
                    "ORDER BY captured_at DESC LIMIT %s",
                ),
                (self._budget, self._max_per_pass),
            )
            return [(row[0], row[1], row[2], row[3]) for row in cur.fetchall()]

    def _renormalise_one(
        self,
        note_id: str,
        before_title: str,
        body: str,
        scope: str,
    ) -> RenameOutcome:
        try:
            classification = self._classifier.classify(
                title=before_title,
                body=body,
                neighbours=[],
                inbox_scope_hint=scope,
            )
        except ClassificationError as exc:
            self._log.warning(
                "renormalise.classify_failed",
                note_id=note_id,
                error=str(exc),
            )
            return RenameOutcome(
                note_id=note_id,
                status="failed",
                before_title=before_title,
                after_title=before_title,
                reason=f"classify-failed: {exc}",
            )

        new_title = classification.title.strip()
        if new_title == before_title:
            return RenameOutcome(
                note_id=note_id,
                status="kept",
                before_title=before_title,
                after_title=before_title,
            )
        if not _meets_contract(new_title, self._budget):
            self._log.info(
                "renormalise.skip_contract_violation",
                note_id=note_id,
                proposed=new_title,
            )
            return RenameOutcome(
                note_id=note_id,
                status="skipped",
                before_title=before_title,
                after_title=before_title,
                reason="proposed title still violates the contract",
            )
        self._apply(note_id, before_title, new_title)
        return RenameOutcome(
            note_id=note_id,
            status="renamed",
            before_title=before_title,
            after_title=new_title,
        )

    def _apply(self, note_id: str, before_title: str, after_title: str) -> None:
        # Audit first so a crash before the UPDATE leaves no stale
        # rename and the audit row alone is recoverable.
        self._audit.record(
            actor=_ACTOR,
            action=_ACTION,
            target_id=note_id,
            target_kind=_TARGET_KIND,
            before={"title": before_title},
            after={"title": after_title},
        )
        with self._store.connection() as conn, conn.cursor() as cur:
            cur.execute(
                sql.SQL("UPDATE kb_notes SET title = %s WHERE id = %s"),
                (after_title, note_id),
            )


def summarise(outcomes: Iterable[RenameOutcome]) -> dict[str, int]:
    """Compact counts for logging / CronJob status."""

    counts = {"renamed": 0, "kept": 0, "skipped": 0, "failed": 0}
    for outcome in outcomes:
        counts[outcome.status] = counts.get(outcome.status, 0) + 1
    return counts
