"""Retroactive topic reclassification, watermark-driven.

Background. The topic vocabulary (`kb_topics`) grows: operators add
slugs, retire old ones, refine aliases. Existing `kb_notes` rows
keep their original `topic:<slug>` until something pokes them.
Without retroactive reclassification, a note captured before the
"hexagonal-architecture" topic existed sits under `software-
architecture` forever even though it'd land in the new bucket if
re-classified today.

Loop:

  1. Read `MAX(created_at)` from `kb_topics` — the "vocab last
     touched" watermark.
  2. Fetch up to `MAX_PER_PASS` rows from `kb_notes` whose
     `topic_classified_at < watermark`, ordered oldest watermark
     first so the most-stale rows clear first.
  3. For each row, re-run the classifier with current vocab. If
     the new topic differs AND confidence ≥
     `RECLASSIFY_CONFIDENCE_FLOOR`, UPDATE the row's scope; either
     way, advance `topic_classified_at = NOW()` so the row isn't
     re-considered until vocab moves again.

Audit rows land in `kb_audit` for every actual scope change.
`actor = kb-reclassify-topics`, `action = reclassify_topic`. No
audit row when the watermark advances without a scope change —
that's a no-op for the operator.

Tag-level reclassification on `rename_tag` admin calls lives in a
follow-up; the watermark column `tag_classified_at` ships with V6
so a future PR can wire it without another migration.
"""

from __future__ import annotations

from collections.abc import Iterable
from dataclasses import dataclass
from typing import Any, Literal, Protocol

import structlog
from psycopg import sql

from curator.audit import AuditRecorder
from curator.classify import ClassificationError, OllamaClassifier
from curator.topics import TopicVocabulary

MAX_PER_PASS = 5
RECLASSIFY_CONFIDENCE_FLOOR = 0.75

_ACTOR = "kb-reclassify-topics"
_ACTION = "reclassify_topic"
_TARGET_KIND = "kb_note"


_OutcomeStatus = Literal[
    "reclassified",
    "unchanged",
    "low_confidence",
    "invalid_topic",
    "failed",
]


@dataclass(frozen=True, slots=True)
class ReclassifyOutcome:
    """Per-row result of one reclassification attempt.

    `reclassified` — scope rewritten + audit row persisted.
    `unchanged`    — classifier returned the same scope; watermark
                     advances, no audit row.
    `low_confidence` — classifier proposed a new scope but its
                     confidence is below the floor; watermark
                     advances, no scope change.
    `invalid_topic` — classifier emitted a topic slug that doesn't
                     pass `TopicVocabulary.slug_for`. Watermark
                     advances; row stays put.
    `failed`        — classifier raised. Row stays untouched
                     including the watermark, so the next pass
                     retries.
    """

    note_id: str
    status: _OutcomeStatus
    before_scope: str
    after_scope: str
    reason: str = ""


class _Store(Protocol):
    def connection(self) -> Any: ...


class TopicReclassifier:
    """Stateless reclassification pass. One instance per CronJob fire."""

    def __init__(
        self,
        *,
        store: _Store,
        classifier: OllamaClassifier,
        audit: AuditRecorder,
        vocabulary: TopicVocabulary,
        confidence_floor: float = RECLASSIFY_CONFIDENCE_FLOOR,
        max_per_pass: int = MAX_PER_PASS,
    ) -> None:
        self._store = store
        self._classifier = classifier
        self._audit = audit
        self._vocabulary = vocabulary
        self._floor = confidence_floor
        self._max_per_pass = max_per_pass
        self._log = structlog.get_logger(__name__)

    def run_pass(self) -> list[ReclassifyOutcome]:
        """One reclassification pass. Returns one outcome per candidate
        the pass touched. Empty list when no candidates qualified or
        when the vocabulary watermark is null (no topics seeded yet).
        """

        watermark = self._fetch_vocab_watermark()
        if watermark is None:
            return []
        candidates = self._fetch_candidates(watermark)
        outcomes: list[ReclassifyOutcome] = []
        for note_id, title, body, scope in candidates:
            outcomes.append(self._reclassify_one(note_id, title, body, scope))
        return outcomes

    def _fetch_vocab_watermark(self) -> Any:
        with self._store.connection() as conn, conn.cursor() as cur:
            cur.execute(sql.SQL("SELECT MAX(created_at) FROM kb_topics"))
            row = cur.fetchall()
            return row[0][0] if row else None

    def _fetch_candidates(self, watermark: Any) -> list[tuple[str, str, str, str]]:
        with self._store.connection() as conn, conn.cursor() as cur:
            cur.execute(
                sql.SQL(
                    "SELECT id, title, body, scope FROM kb_notes "
                    "WHERE topic_classified_at < %s "
                    "ORDER BY topic_classified_at ASC LIMIT %s",
                ),
                (watermark, self._max_per_pass),
            )
            return [(row[0], row[1], row[2], row[3]) for row in cur.fetchall()]

    def _reclassify_one(
        self,
        note_id: str,
        title: str,
        body: str,
        before_scope: str,
    ) -> ReclassifyOutcome:
        try:
            classification = self._classifier.classify(
                title=title,
                body=body,
                neighbours=[],
                inbox_scope_hint=before_scope,
            )
        except ClassificationError as exc:
            self._log.warning(
                "reclassify.classify_failed",
                note_id=note_id,
                error=str(exc),
            )
            return ReclassifyOutcome(
                note_id=note_id,
                status="failed",
                before_scope=before_scope,
                after_scope=before_scope,
                reason=f"classify-failed: {exc}",
            )

        new_scope = classification.scope
        if new_scope == before_scope:
            self._advance_watermark(note_id)
            return ReclassifyOutcome(
                note_id=note_id,
                status="unchanged",
                before_scope=before_scope,
                after_scope=before_scope,
            )
        if classification.is_topic_scope():
            canonical = self._vocabulary.slug_for(new_scope.removeprefix("topic:"))
            if canonical is None:
                self._advance_watermark(note_id)
                return ReclassifyOutcome(
                    note_id=note_id,
                    status="invalid_topic",
                    before_scope=before_scope,
                    after_scope=before_scope,
                    reason=f"unknown topic slug: {new_scope}",
                )
            new_scope = f"topic:{canonical}"
        if classification.confidence < self._floor:
            self._advance_watermark(note_id)
            return ReclassifyOutcome(
                note_id=note_id,
                status="low_confidence",
                before_scope=before_scope,
                after_scope=before_scope,
                reason=f"confidence {classification.confidence:.2f} below floor {self._floor:.2f}",
            )
        self._apply(note_id, before_scope, new_scope)
        return ReclassifyOutcome(
            note_id=note_id,
            status="reclassified",
            before_scope=before_scope,
            after_scope=new_scope,
        )

    def _apply(self, note_id: str, before_scope: str, after_scope: str) -> None:
        # Audit first so a crash before UPDATE leaves a recoverable record.
        self._audit.record(
            actor=_ACTOR,
            action=_ACTION,
            target_id=note_id,
            target_kind=_TARGET_KIND,
            before={"scope": before_scope},
            after={"scope": after_scope},
        )
        with self._store.connection() as conn, conn.cursor() as cur:
            cur.execute(
                sql.SQL(
                    "UPDATE kb_notes SET scope = %s, topic_classified_at = NOW() WHERE id = %s",
                ),
                (after_scope, note_id),
            )

    def _advance_watermark(self, note_id: str) -> None:
        with self._store.connection() as conn, conn.cursor() as cur:
            cur.execute(
                sql.SQL(
                    "UPDATE kb_notes SET topic_classified_at = NOW() WHERE id = %s",
                ),
                (note_id,),
            )


def summarise(outcomes: Iterable[ReclassifyOutcome]) -> dict[str, int]:
    counts: dict[str, int] = {
        "reclassified": 0,
        "unchanged": 0,
        "low_confidence": 0,
        "invalid_topic": 0,
        "failed": 0,
    }
    for outcome in outcomes:
        counts[outcome.status] = counts.get(outcome.status, 0) + 1
    return counts
