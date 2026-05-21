"""Relation enrichment — back-fill ``see_also`` edges for older notes
that promoted without neighbours.

Earliest promotions (pre-recall and pre-Qwen3-32B) often have empty
``kb_relations`` outgoing edges: the inbox classifier was running on
a smaller model and didn't always propose ``see_also`` targets even
when good neighbours existed. This pass walks promoted notes whose
``see_also`` edge count is zero, runs them back through the same
classifier the inbox pass uses (now on qwen3:32b via the heavy
gateway), and writes any newly-proposed edges to ``kb_relations``.

The pass intentionally:

- **Does not change ``kb_notes.title`` / ``scope`` / ``type`` /
  ``tags``.** The original promotion is canonical for those; the
  classifier's re-issued values are discarded. Only the ``see_also``
  field is consumed. Title quality is handled separately by
  :class:`curator.orchestrator.passes.title_quality.TitleQualityPass`.
- **Does not touch the vault file.** ``kb_relations`` is the
  canonical store for edge data; writing into the file's frontmatter
  would create churn the recall path doesn't need. The on-disk
  frontmatter gets refreshed on the next title-quality pass or on
  the next time the note is re-promoted.
- **Validates target IDs exist in ``kb_notes``** before inserting.
  The classifier sometimes hallucinates `kb_…` identifiers; the
  `existing_ids` filter keeps dead edges out.

Watermark: just records the model used for the last successful run.
A model change re-qualifies every empty-see_also note.
"""

from __future__ import annotations

import json

import httpx
import structlog
from pydantic import ValidationError

from curator.classify import (
    ClassificationError,
    Neighbour,
    OllamaClassifier,
)
from curator.orchestrator.protocol import PassOutcome, PassState
from curator.projects import ProjectVocabulary
from curator.recall import RecallClient
from curator.store import CuratorStore
from curator.topics import TopicVocabulary

log = structlog.get_logger(__name__)


class RelationEnrichmentPass:
    """Back-fill ``see_also`` edges. Name: ``'relation_enrichment'``."""

    name = "relation_enrichment"

    def __init__(
        self,
        *,
        store: CuratorStore,
        recall: RecallClient,
        topics: TopicVocabulary,
        projects: ProjectVocabulary,
        http_client: httpx.Client,
        chat_base_url: str,
        chat_model: str,
        chat_timeout_seconds: float,
        min_age_days: int = 7,
        batch_size: int = 20,
        recall_limit: int = 5,
    ) -> None:
        self._store = store
        self._recall = recall
        self._topics = topics
        self._projects = projects
        self._http = http_client
        self._chat_base_url = chat_base_url
        self._chat_model = chat_model
        self._chat_timeout = chat_timeout_seconds
        self._min_age_days = min_age_days
        self._batch_size = batch_size
        self._recall_limit = recall_limit

    def has_work(self, state: PassState) -> bool:
        return bool(
            self._store.select_relation_enrichment_batch(min_age_days=self._min_age_days, limit=1)
        )

    def run(self, state: PassState) -> PassOutcome:
        candidates = self._store.select_relation_enrichment_batch(
            min_age_days=self._min_age_days,
            limit=self._batch_size,
        )
        log.info("relation_enrichment.pass_start", candidates=len(candidates))
        if not candidates:
            return PassOutcome(status="no_work", notes_processed=0)

        classifier = OllamaClassifier(
            base_url=self._chat_base_url,
            model=self._chat_model,
            topic_slugs=self._topics.slugs,
            project_slugs=self._projects.slugs,
            timeout_seconds=self._chat_timeout,
            client=self._http,
        )

        notes_processed = 0
        edges_added = 0
        for note_id, title, body, scope in candidates:
            try:
                neighbours = self._neighbours_for(note_id, title, body)
                classification = classifier.classify(
                    title=title,
                    body=body,
                    neighbours=neighbours,
                    inbox_scope_hint=scope,
                )
            except (ClassificationError, ValidationError) as exc:
                log.warning(
                    "relation_enrichment.classify_failed",
                    note_id=note_id,
                    error=str(exc),
                )
                continue
            edges_added += self._write_see_also_edges(
                subject_id=note_id,
                proposed=classification.see_also,
            )
            notes_processed += 1

        log.info(
            "relation_enrichment.complete",
            notes_processed=notes_processed,
            edges_added=edges_added,
            model=self._chat_model,
        )
        return PassOutcome(
            status="success" if notes_processed > 0 else "no_work",
            notes_processed=notes_processed,
            watermark_after={
                "model": self._chat_model,
                "last_candidates": len(candidates),
                "last_edges_added": edges_added,
            },
        )

    # -- helpers -----------------------------------------------------

    def _neighbours_for(
        self,
        note_id: str,
        title: str,
        body: str,
    ) -> list[Neighbour]:
        # Cap the query payload so a long body does not blow the
        # recall-side embedding budget. The inbox curator uses the
        # same shape.
        query = f"{title}\n\n{body[:2000]}"
        try:
            hits = self._recall.recall(query=query, limit=self._recall_limit)
        except Exception as exc:
            log.warning("relation_enrichment.recall_failed", note_id=note_id, error=str(exc))
            return []
        neighbours: list[Neighbour] = []
        for hit in hits:
            if hit.id == note_id:
                # Self-match — happens when the recall path retrieves
                # the same note we're enriching. Skip.
                continue
            neighbours.append(
                Neighbour(id=hit.id, title=hit.title, scope=hit.scope, snippet=hit.snippet)
            )
        return neighbours

    def _write_see_also_edges(
        self,
        *,
        subject_id: str,
        proposed: list[str],
    ) -> int:
        if not proposed:
            return 0
        existing = self._store.existing_ids(proposed)
        added = 0
        for target in proposed:
            if target == subject_id or target not in existing:
                continue
            self._store.insert_relation(
                subject_id=subject_id,
                predicate="see_also",
                object_id=target,
                props_json=json.dumps({"source": "relation_enrichment"}),
            )
            added += 1
        return added
