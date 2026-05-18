"""Per-inbox-file orchestrator.

For each `_inbox/<day>/<HHMMSS>-<slug>--<id8>.md`:

1. Parse the frontmatter to recover id + captured_at + the worker-
   written body.
2. Embed the body via Ollama (kept here for forward-compat with the
   pgvector ANN leg the next slice ships; this slice's classifier
   already consumes recall hits from the FTS path).
3. Pull top-K nearest existing notes via the knowledge-api recall
   tool.
4. Call the Ollama classifier; on validation failure, route to
   `_inbox/_needs-review/`.
5. Validate the classifier's `topic` against the closed vocabulary;
   validate `supersedes` / `see_also` targets exist in `kb_notes`.
6. Decide the destination folder, run `git mv`, rewrite the
   frontmatter + body, commit + push.
7. UPDATE `kb_notes.scope/vault_path/vault_commit/confidence` and
   INSERT `kb_relations` rows.

Idempotent: a re-run on the same `_inbox/` file finds the file
already moved and skips. Failed promotes leave the file in place
for the next pass.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import structlog

from curator.classify import (
    Classification,
    ClassificationError,
    Neighbour,
    OllamaClassifier,
)
from curator.lightrag import LightRagClient, LightRagDocument
from curator.recall import RecallClient, RecallHit
from curator.store import CuratorStore
from curator.topics import TopicVocabulary
from curator.vault import (
    CuratorVault,
    NoteFrontmatter,
    folder_for_scope,
    parse_note_file,
    render_note_file,
    resolve_destination,
)

log = structlog.get_logger(__name__)


@dataclass(frozen=True, slots=True)
class PromoteOutcome:
    note_id: str
    status: str  # "promoted" | "needs_review" | "skipped"
    destination_rel: str
    reason: str = ""


class Promoter:
    """Glue object: holds the four collaborators + the policy knobs."""

    def __init__(
        self,
        *,
        classifier: OllamaClassifier,
        recall: RecallClient,
        store: CuratorStore,
        vault: CuratorVault,
        topics: TopicVocabulary,
        clone_dir: Path,
        confidence_floor: float,
        recall_limit: int,
        lightrag: LightRagClient | None = None,
    ) -> None:
        self._classifier = classifier
        self._recall = recall
        self._store = store
        self._vault = vault
        self._topics = topics
        self._clone_dir = clone_dir
        self._confidence_floor = confidence_floor
        self._recall_limit = recall_limit
        self._lightrag = lightrag

    def promote_inbox_file(self, inbox_rel: str) -> PromoteOutcome:
        path = self._clone_dir / inbox_rel
        if not path.exists():
            return PromoteOutcome(
                note_id="",
                status="skipped",
                destination_rel=inbox_rel,
                reason="file gone (race with another pass)",
            )
        front = parse_note_file(path)
        if not front.id:
            return self._send_to_review(inbox_rel, reason="missing-id-in-frontmatter")
        neighbours = self._fetch_neighbours(front)
        try:
            classification = self._classifier.classify(
                title=front.title or "untitled",
                body=front.body,
                neighbours=[
                    Neighbour(id=n.id, title=n.title, scope=n.scope, snippet=n.snippet)
                    for n in neighbours
                ],
                inbox_scope_hint=front.scope,
            )
        except ClassificationError as exc:
            return self._send_to_review(inbox_rel, reason=f"classify-failed:{type(exc).__name__}")

        if classification.needs_review_reason:
            return self._send_to_review(
                inbox_rel, reason=f"model-flagged:{classification.needs_review_reason}"
            )
        if classification.confidence < self._confidence_floor:
            return self._send_to_review(
                inbox_rel,
                reason=f"low-confidence:{classification.confidence:.2f}<{self._confidence_floor}",
            )
        topic_check = self._validate_topic(classification)
        if topic_check is not None:
            return self._send_to_review(inbox_rel, reason=topic_check)
        if not self._validate_relation_targets(classification):
            return self._send_to_review(inbox_rel, reason="relation-target-missing")

        dest_abs = resolve_destination(
            clone_dir=self._clone_dir,
            folder=folder_for_scope(classification.scope),
            note_type=classification.type,
            title=classification.title,
            note_id=front.id,
        )
        dest_rel = str(dest_abs.relative_to(self._clone_dir))

        new_body = render_note_file(
            front=front,
            new_title=classification.title,
            new_scope=classification.scope,
            new_type=classification.type,
            new_tags=tuple(classification.tags),
            new_confidence=classification.confidence,
            supersedes=tuple(classification.supersedes),
            see_also=tuple(classification.see_also),
        )
        slug = dest_abs.stem
        commit_subject = f"curator({classification.scope}): promote {slug}"
        result = self._vault.promote(
            source_rel=inbox_rel,
            destination_rel=dest_rel,
            new_body=new_body,
            commit_subject=commit_subject,
        )

        rows = self._store.promote_note(
            note_id=front.id,
            scope=classification.scope,
            vault_path=result.new_relative_path,
            vault_commit=result.commit_sha,
            confidence=classification.confidence,
        )
        if rows == 0:
            log.warning("curator.promote_orphan_db_row", note_id=front.id)
        for target in classification.supersedes:
            self._store.insert_relation(
                subject_id=front.id, predicate="supersedes", object_id=target
            )
        for target in classification.see_also:
            self._store.insert_relation(subject_id=front.id, predicate="see_also", object_id=target)
        if self._lightrag is not None:
            # Fire-and-forget: a slow / down LightRAG must not block
            # the promotion's git + DB writes. The next pass that
            # touches this id reconciles via LightRAG's doc-status.
            self._lightrag.publish(
                LightRagDocument(
                    id=front.id,
                    title=classification.title,
                    body=front.body,
                    scope=classification.scope,
                    type=classification.type,
                ),
            )
        return PromoteOutcome(
            note_id=front.id,
            status="promoted",
            destination_rel=result.new_relative_path,
        )

    # ----- helpers -----

    def _fetch_neighbours(self, front: NoteFrontmatter) -> list[RecallHit]:
        if not front.title and not front.body.strip():
            return []
        query = (front.title + "\n" + front.body[:600]).strip()
        try:
            return self._recall.recall(query=query, limit=self._recall_limit)
        except Exception as exc:  # pragma: no cover — best-effort
            log.warning("curator.recall_failed", error=str(exc))
            return []

    def _validate_topic(self, classification: Classification) -> str | None:
        if not classification.is_topic_scope():
            return None
        # The scope is `topic:<slug>` — verify the slug is in the
        # closed vocabulary. The classifier already pattern-matches
        # the shape, but the actual vocabulary is policy.
        slug = classification.scope.split(":", 1)[1]
        if self._topics.slug_for(slug) is None:
            return f"unknown-topic-slug:{slug}"
        return None

    def _validate_relation_targets(self, classification: Classification) -> bool:
        targets = {*classification.supersedes, *classification.see_also}
        if not targets:
            return True
        existing = self._store.existing_ids(targets)
        return existing == targets

    def _send_to_review(self, inbox_rel: str, reason: str) -> PromoteOutcome:
        result = self._vault.move_to_needs_review(source_rel=inbox_rel, reason=reason)
        return PromoteOutcome(
            note_id="",
            status="needs_review",
            destination_rel=result.new_relative_path,
            reason=reason,
        )
