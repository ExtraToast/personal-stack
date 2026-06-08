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
   `_inbox/_needs-review/` or discard after repeated review attempts.
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
from curator.embed import OllamaEmbedder
from curator.lightrag import LightRagClient, LightRagDocument
from curator.projects import ProjectVocabulary
from curator.recall import RecallClient, RecallHit
from curator.relations import RelationResolver
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
    status: str  # "promoted" | "needs_review" | "discarded" | "skipped"
    destination_rel: str
    reason: str = ""


@dataclass(frozen=True, slots=True)
class ScopeOutcome:
    """Result of validating + canonicalising the classifier-emitted
    scope against the closed topic / project vocabularies.

    `scope` carries the canonical form on success (e.g. an alias
    has been folded into the canonical slug); `error` carries the
    needs-review reason on failure. Exactly one is set per call.
    """

    scope: str = ""
    error: str | None = None


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
        projects: ProjectVocabulary | None = None,
        confidence_floor: float,
        max_review_attempts: int,
        recall_limit: int,
        lightrag: LightRagClient | None = None,
        resolver: RelationResolver | None = None,
        embedder: OllamaEmbedder | None = None,
        embedding_model: str | None = None,
    ) -> None:
        self._classifier = classifier
        self._recall = recall
        self._store = store
        self._vault = vault
        self._topics = topics
        # An empty `ProjectVocabulary` routes every `project:<...>`
        # emission to needs-review — exactly the right default
        # before the V8 seed migration runs (or in tests that
        # don't care about projects).
        self._projects = projects or ProjectVocabulary([])
        self._clone_dir = clone_dir
        self._confidence_floor = confidence_floor
        self._max_review_attempts = max_review_attempts
        self._recall_limit = recall_limit
        self._lightrag = lightrag
        # Default resolver wires the existing recall + store. The
        # caller can inject a stricter / looser variant via tests or
        # to feature-flag the behaviour off temporarily (pass a
        # resolver whose `resolve` always returns the originals).
        self._resolver = resolver or RelationResolver(recall=recall, store=store)
        # Embedder feeds the recall side's pgvector ANN leg (PR-1 of
        # the recall stack added the column; this Promoter is the
        # writer). Optional so tests + the V1 path that ran before
        # embeddings existed still work — when None, promotes still
        # succeed and the row carries a NULL embedding the backfill
        # job picks up later.
        self._embedder = embedder
        # The model name persisted alongside the vector. Used as the
        # backfill watermark — a future model swap surfaces every
        # row whose `embedding_model` diverges as a candidate to
        # re-embed.
        self._embedding_model = embedding_model or (embedder.model if embedder else "")

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
            return self._send_to_review(
                inbox_rel, reason="missing-id-in-frontmatter", front=front
            )
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
            return self._send_to_review(
                inbox_rel,
                reason=f"classify-failed:{type(exc).__name__}",
                front=front,
            )

        if classification.action == "discard":
            return self._discard(
                inbox_rel,
                front,
                reason=f"model-discard:{classification.needs_review_reason or 'low-value'}",
            )
        if classification.needs_review_reason:
            return self._send_to_review(
                inbox_rel,
                reason=f"model-flagged:{classification.needs_review_reason}",
                front=front,
            )
        if classification.confidence < self._confidence_floor:
            return self._send_to_review(
                inbox_rel,
                reason=f"low-confidence:{classification.confidence:.2f}<{self._confidence_floor}",
                front=front,
            )
        scope_outcome = self._canonicalise_scope(classification)
        if scope_outcome.error is not None:
            return self._send_to_review(inbox_rel, reason=scope_outcome.error, front=front)
        canonical_scope = scope_outcome.scope
        if canonical_scope != classification.scope:
            # An alias matched — `personal-stack-2 -> personal-stack`,
            # `esa-blueshell-website -> website`, etc. Log so an
            # operator can later audit how often the classifier
            # needed rescue.
            log.info(
                "curator.scope_canonicalised",
                note_id=front.id,
                from_scope=classification.scope,
                to_scope=canonical_scope,
            )
        # Resolve relation targets gracefully — substitute via FTS
        # recall when the classifier emits a phrase-shaped pseudo-id,
        # drop the dead edge otherwise. Note still promotes either
        # way; only the graph edge can be lost. Audit via structlog
        # so a Grafana panel can flag drift.
        resolution = self._resolver.resolve(
            supersedes=classification.supersedes,
            see_also=classification.see_also,
        )
        if resolution.has_changes:
            log.info(
                "curator.relations_resolved",
                note_id=front.id,
                substituted=list(resolution.substituted),
                dropped=list(resolution.dropped),
            )

        dest_abs = resolve_destination(
            clone_dir=self._clone_dir,
            folder=folder_for_scope(canonical_scope),
            note_type=classification.type,
            title=classification.title,
            note_id=front.id,
        )
        dest_rel = str(dest_abs.relative_to(self._clone_dir))

        new_body = render_note_file(
            front=front,
            new_title=classification.title,
            new_scope=canonical_scope,
            new_type=classification.type,
            new_tags=tuple(classification.tags),
            new_confidence=classification.confidence,
            supersedes=resolution.supersedes,
            see_also=resolution.see_also,
        )
        slug = dest_abs.stem
        commit_subject = f"curator({canonical_scope}): promote {slug}"
        result = self._vault.promote(
            source_rel=inbox_rel,
            destination_rel=dest_rel,
            new_body=new_body,
            commit_subject=commit_subject,
        )

        rows = self._store.promote_note(
            note_id=front.id,
            scope=canonical_scope,
            vault_path=result.new_relative_path,
            vault_commit=result.commit_sha,
            confidence=classification.confidence,
        )
        if rows == 0:
            log.warning("curator.promote_orphan_db_row", note_id=front.id)
        for target in resolution.supersedes:
            self._store.insert_relation(
                subject_id=front.id, predicate="supersedes", object_id=target
            )
        for target in resolution.see_also:
            self._store.insert_relation(subject_id=front.id, predicate="see_also", object_id=target)

        # Embed + persist for the recall path's pgvector ANN leg. Soft
        # failure: an Ollama outage must not block the promote, the
        # backfill CronJob picks the row up next pass. Skip when no
        # embedder is wired (e.g. tests, or the bring-up window before
        # the V9 schema lands in a target environment).
        if self._embedder is not None and self._embedding_model:
            try:
                embedding = self._embedder.embed(
                    f"{classification.title}\n\n{front.body}",
                )
                self._store.write_embedding(
                    note_id=front.id,
                    vector=embedding.vector,
                    model=self._embedding_model,
                )
            except Exception as exc:
                log.warning(
                    "curator.embed_skipped",
                    note_id=front.id,
                    model=self._embedding_model,
                    error=str(exc),
                )
        if self._lightrag is not None:
            # Fire-and-forget: a slow / down LightRAG must not block
            # the promotion's git + DB writes. The next pass that
            # touches this id reconciles via LightRAG's doc-status.
            self._lightrag.publish(
                LightRagDocument(
                    id=front.id,
                    title=classification.title,
                    body=front.body,
                    scope=canonical_scope,
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

    def _canonicalise_scope(self, classification: Classification) -> ScopeOutcome:
        """Validate the emitted scope against the closed vocabularies
        and return the canonical form.

        - `topic:<slug>` — look up `slug` in `TopicVocabulary`.
          When the slug is an alias the result is the canonical
          slug; the scope rewrites to `topic:<canonical>` so the
          vault folder is consistent.
        - `project:<slug>` — same shape against
          `ProjectVocabulary`. Catches the common classifier
          hallucinations (`personal-stack-2`,
          `esa-blueshell/website`, `esa-blueshell-website`,
          `esa-blueshell.website`, `github-actions`,
          `home-direct`, `my-kubernetes-observability-stack`).
          Unknown slugs route to needs-review with reason
          `unknown-project-slug:<emitted>`.
        - `agent:<name>` — accepted as-is (no closed vocabulary
          yet; the inbox-side regex on `_SCOPE_PATTERN` is the
          only gate).
        """

        scope = classification.scope
        if classification.is_topic_scope():
            slug = scope.split(":", 1)[1]
            canonical = self._topics.slug_for(slug)
            if canonical is None:
                return ScopeOutcome(error=f"unknown-topic-slug:{slug}")
            return ScopeOutcome(scope=f"topic:{canonical}")
        if scope.startswith("project:"):
            slug = scope.split(":", 1)[1]
            canonical = self._projects.slug_for(slug)
            if canonical is None:
                return ScopeOutcome(error=f"unknown-project-slug:{slug}")
            return ScopeOutcome(scope=f"project:{canonical}")
        # agent:* and any other shape the classifier might emit
        # passes through unchanged. `_SCOPE_PATTERN` in classify.py
        # is the gate on the shape itself.
        return ScopeOutcome(scope=scope)

    def _send_to_review(
        self,
        inbox_rel: str,
        reason: str,
        front: NoteFrontmatter | None = None,
    ) -> PromoteOutcome:
        prev = 0
        if front is not None:
            try:
                prev = int(front.other.get("review_attempts", "0"))
            except ValueError:
                prev = 0
        attempt = prev + 1
        if attempt > self._max_review_attempts:
            return self._discard(inbox_rel, front, reason=f"stale-review:{reason}")
        result = self._vault.move_to_needs_review(
            source_rel=inbox_rel,
            reason=reason,
            attempt=attempt,
        )
        return PromoteOutcome(
            note_id=(front.id if front else ""),
            status="needs_review",
            destination_rel=result.new_relative_path,
            reason=reason,
        )

    def _discard(
        self,
        inbox_rel: str,
        front: NoteFrontmatter | None,
        reason: str,
    ) -> PromoteOutcome:
        result = self._vault.discard(source_rel=inbox_rel, reason=reason)
        note_id = front.id if front else ""
        if note_id:
            self._store.discard_note(note_id=note_id)
        log.info(
            "curator.discarded",
            note_id=note_id,
            destination=result.new_relative_path,
            reason=reason,
        )
        return PromoteOutcome(
            note_id=note_id,
            status="discarded",
            destination_rel=result.new_relative_path,
            reason=reason,
        )
