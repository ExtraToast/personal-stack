"""Graceful resolution of classifier-emitted `supersedes` / `see_also`
targets that don't exist in `kb_notes`.

Background. The Ollama classifier emits 0-10 supersedes + see_also
ids per note. Earlier curator behaviour: validate every id against
the DB; if ANY one missed, route the whole note to
`_inbox/_needs-review/` with reason `relation-target-missing`. Most
single missing-id rejections were the model hallucinating one bad
target out of an otherwise-good set — keeping the note out of the
KB to preserve graph purity was a bad trade.

This module replaces the binary check with a resolver:

  1. If the target string already exists in `kb_notes.id`, keep it.
  2. If it isn't a ULID-shaped string (probably a title fragment
     the model emitted instead of an id), call `knowledge.recall`
     with it as the query. If the top hit's score crosses
     `substitution_score_floor`, substitute its real id.
  3. (Stub) pgvector ANN substitution lands when the ANN leg of
     `RecallRepository` ships.
  4. Otherwise: drop the dead edge but keep the note. The note's
     body still mentions the conceptual links in prose; only the
     formal graph edge is lost, and the audit log records it.

The caller iterates `resolution.substituted` + `resolution.dropped`
to emit a structured log row per change so a Grafana panel can
flag drift in either direction.

The Plan B audit-table integration (kb_audit) lands in its own PR;
for now the structured log row is the audit trail.
"""

from __future__ import annotations

import re
from collections.abc import Iterable, Sequence
from dataclasses import dataclass

from curator.recall import RecallClient
from curator.store import CuratorStore

# ULIDs are 26 chars from Crockford's base32 set. The classifier's
# JSON schema does not enforce the alphabet (any string goes), but
# a string matching this pattern + length is "ULID-shaped" — and a
# kb-recall against a ULID-shaped string almost never returns a
# useful hit (the FTS index has no overlap with a random-looking id).
_ULID_RE = re.compile(r"^[0-9A-HJKMNP-TV-Z]{26}$")


@dataclass(frozen=True, slots=True)
class RelationResolution:
    """The outcome of a single resolver pass.

    `supersedes` / `see_also` hold the post-resolution id lists the
    caller should actually persist. `substituted` records every
    (predicate, original, resolved) the resolver was able to rescue
    via recall; `dropped` records every (predicate, original) it
    gave up on. `has_changes` lets the caller skip log emission
    when nothing happened.
    """

    supersedes: tuple[str, ...]
    see_also: tuple[str, ...]
    substituted: tuple[tuple[str, str, str], ...]
    dropped: tuple[tuple[str, str], ...]

    @property
    def has_changes(self) -> bool:
        return bool(self.substituted) or bool(self.dropped)


class RelationResolver:
    """Stateless resolver for missing relation targets. One instance
    per `Promoter`. Caller-supplied [recall] + [store] are used as
    pure functions; no internal state survives between `resolve` calls.

    Knobs:
      - `substitution_score_floor`: minimum `ts_rank` score on the
        top recall hit before the resolver will substitute. Default
        0.7 — conservative; a substitution that's wrong silently
        misroutes graph edges, so the cost of a false positive is
        higher than the cost of a drop. Lower this if drops dominate.
      - `recall_limit`: how many hits to ask recall for. The
        resolver only ever uses the top one, but a higher limit
        slightly increases the chance of a relevant hit in the
        window when ts_rank is noisy.
    """

    def __init__(
        self,
        *,
        recall: RecallClient,
        store: CuratorStore,
        substitution_score_floor: float = 0.7,
        recall_limit: int = 3,
    ) -> None:
        self._recall = recall
        self._store = store
        self._score_floor = substitution_score_floor
        self._recall_limit = recall_limit

    def resolve(
        self,
        *,
        supersedes: Sequence[str],
        see_also: Sequence[str],
    ) -> RelationResolution:
        all_targets = {*supersedes, *see_also}
        if not all_targets:
            return _empty(supersedes, see_also)
        existing = self._store.existing_ids(all_targets)
        missing = all_targets - existing
        if not missing:
            return _empty(supersedes, see_also)
        rescued = self._try_substitute(missing)

        resolved_supersedes, sub_super, drop_super = _apply(
            supersedes, existing, rescued, predicate="supersedes"
        )
        resolved_see_also, sub_see, drop_see = _apply(
            see_also, existing, rescued, predicate="see_also"
        )
        return RelationResolution(
            supersedes=resolved_supersedes,
            see_also=resolved_see_also,
            substituted=sub_super + sub_see,
            dropped=drop_super + drop_see,
        )

    def _try_substitute(self, missing: Iterable[str]) -> dict[str, str]:
        """Run recall for every missing target, return a mapping of
        `original -> resolved_id` for the ones that crossed the
        score floor. ULID-shaped strings are skipped — FTS recall
        against a random id is wasted budget.
        """

        out: dict[str, str] = {}
        for target in missing:
            if not target or _ULID_RE.match(target):
                continue
            try:
                hits = self._recall.recall(query=target, limit=self._recall_limit)
            except Exception:
                continue
            if not hits:
                continue
            top = hits[0]
            if top.score >= self._score_floor and top.id and top.id != target:
                out[target] = top.id
        return out


def _empty(supersedes: Sequence[str], see_also: Sequence[str]) -> RelationResolution:
    return RelationResolution(
        supersedes=tuple(supersedes),
        see_also=tuple(see_also),
        substituted=(),
        dropped=(),
    )


def _apply(
    originals: Sequence[str],
    existing: set[str],
    rescued: dict[str, str],
    *,
    predicate: str,
) -> tuple[
    tuple[str, ...],
    tuple[tuple[str, str, str], ...],
    tuple[tuple[str, str], ...],
]:
    """Walk the classifier's emitted list once, building the
    post-resolution list + the substituted / dropped audit tuples.
    """

    kept: list[str] = []
    substituted: list[tuple[str, str, str]] = []
    dropped: list[tuple[str, str]] = []
    for original in originals:
        if original in existing:
            kept.append(original)
        elif original in rescued:
            kept.append(rescued[original])
            substituted.append((predicate, original, rescued[original]))
        else:
            dropped.append((predicate, original))
    return tuple(kept), tuple(substituted), tuple(dropped)
