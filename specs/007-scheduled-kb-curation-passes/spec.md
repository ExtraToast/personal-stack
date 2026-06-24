# Feature Specification: Scheduled KB Curation Passes

**Feature Branch**: `007-scheduled-kb-curation-passes`
**Created**: 2026-06-24
**Status**: Draft
**Input**: Replace the retired always-on knowledge-curator with cheap, bounded, scheduled curation passes that keep the shared knowledge base high-signal without burning standing compute.

## Context & Verified Ground Truth

*Verified against the personal-stack-2 tree on 2026-06-24.*

- The previous heavyweight `knowledge-curator` was retired: it is absent from `platform/cluster/flux/apps/knowledge/kustomization.yaml`, and `knowledge-api` runs `KNOWLEDGE_MODE: lite` (`platform/cluster/flux/apps/knowledge/knowledge-api/deployment.yaml`), which "expose[s] only recall + capture over MCP and drop[s] the curator-governance tools" — so the `review_summary` bucket tool is **not available**. Curation must be built on the lite toolset: `recall`, `list_recent`, `find_conflicts`, `relations`, `get_note`, `ingest_note`, `capture_*`.
- A Tier-1 daily triage CronJob (`agents-kb-curator-triage`) already exists in dry-run form (`platform/cluster/flux/apps/agents/kb-curator/cronjob.yaml`): headless `claude -p` (Haiku), read-only tools, proposes triage to Job logs, mutates nothing.
- In-cluster conventions are set by `refresh-ping` and `kb-install` (`platform/cluster/flux/apps/agents/`): the `agent-runner` image, the `claude-credentials` PVC, the `agents-kb-bearer` secret (key `bearer`), the `enschede-gtx-960m-1` node, and the in-cluster `knowledge-api.knowledge-system.svc.cluster.local:8080` endpoint.
- Captures land continuously from auto-capture hooks (git-commit messages, session digests); many are low-signal and accumulate without triage.

This feature formalizes the curation system end to end: the already-shipped Tier-1 dry-run, its promotion to apply-mode, and a new Tier-2 weekly consolidation pass — all under shared guardrails that keep cost bounded.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Bounded daily triage that costs almost nothing (Priority: P1)

As the platform operator, I want a daily pass that surfaces the lowest-signal and most-fixable recent captures, so the inbox never rots — without a standing agent consuming GPU/compute.

**Why this priority**: This is the direct replacement for the retired curator and the cheapest, highest-frequency safety net. It must exist before any write automation.

**Independent Test**: Trigger the daily Job on demand; confirm it reads only a bounded slice of recent notes, emits a triage report to its logs, completes within its time/turn budget, and changes nothing in the KB.

**Acceptance Scenarios**:

1. **Given** the daily CronJob in dry-run, **When** it runs, **Then** it inspects at most the configured note cap, emits a markdown triage report (keep/scope, discard, dedup, needs-review) to stdout, and performs zero KB writes.
2. **Given** a run that would exceed its tool-call or turn budget, **When** the cap is reached, **Then** the pass stops, reports what it covered, and defers the remainder to a later run.
3. **Given** the in-cluster knowledge-api is unreachable, **When** the Job runs, **Then** it fails loudly in its logs without corrupting any state.

---

### User Story 2 - Promote daily triage to safe apply-mode (Priority: P1)

As the operator, once I trust the triage quality, I want the daily pass to apply only **additive, reversible** metadata (scope, tags, confidence) automatically, so obvious housekeeping happens without me.

**Why this priority**: Triage that only proposes still requires a human for every trivial fix. Auto-applying the safe, reversible class is where the labor savings begin — but it must be gated and never lossy.

**Independent Test**: Enable apply-mode via configuration; confirm the pass sets scope/tags/confidence on untriaged notes, marks discard/dup candidates with reversible `_*-candidate` tags, deletes nothing, and is idempotent on re-run.

**Acceptance Scenarios**:

1. **Given** apply-mode enabled, **When** the pass triages an unscoped durable note, **Then** it assigns a scope and up to a bounded number of tags, and leaves note content unchanged.
2. **Given** a low-signal note, **When** the pass classifies it as discard, **Then** it applies a `_discard-candidate` tag (not a deletion), which a human or Tier-2 later confirms.
3. **Given** a note already stamped `curated:triage:<date>` this cycle, **When** the pass runs again, **Then** it is a no-op for that note (idempotent).
4. **Given** any classification the pass is unsure about, **When** it processes the note, **Then** it tags `_needs-review` with a one-line reason and applies no other change.

---

### User Story 3 - Weekly consolidation proposed as a reviewable PR (Priority: P2)

As the operator, I want a heavier weekly pass that consolidates the knowledge base — merging duplicates, fixing tag/slug drift, surfacing conflicts, decaying stale notes, and rolling many small lessons into canonical notes — and proposes every lossy change as a **single PR** I can review, so quality improves without me trusting an agent to rewrite the KB unattended.

**Why this priority**: This is the highest-value curation, but every action is lossy or editorial, so it must be human-gated. It depends on the cheaper tiers being in place first.

**Independent Test**: Trigger the weekly Job; confirm it opens exactly one PR against the knowledge-vault repo containing proposed merges/renames/rollups/archive-candidates and a conflict digest, auto-merges nothing, and applies no destructive delete directly to the KB.

**Acceptance Scenarios**:

1. **Given** near-duplicate notes, **When** the weekly pass runs, **Then** it stages a merge (survivor chosen, losers set to supersede the survivor) in the PR — never an unattended deletion.
2. **Given** near-duplicate tags or topic slugs, **When** the pass runs, **Then** it stages canonicalizing renames against the closed vocabulary in the PR.
3. **Given** genuinely contradictory notes, **When** the pass runs, **Then** it records both note ids and the conflicting claims in a `_needs-review` digest and never auto-resolves the conflict.
4. **Given** notes unused past the staleness threshold, **When** the pass runs, **Then** it lowers their confidence one step and tags `_archive-candidate` — it never deletes.
5. **Given** several small lessons sharing scope and topic, **When** the pass runs, **Then** it drafts one canonical rollup note in the PR with the originals staged to supersede it.
6. **Given** a completed weekly run, **When** it finishes, **Then** exactly one PR exists, titled for the week, and it is never auto-merged.

---

### Edge Cases

- A run hits its budget mid-queue → it stops cleanly and the queue drains over subsequent runs (no unbounded catch-up).
- A retrieval/tool call fails for one note → that note is skipped, the pass continues, and the failure is reported.
- The closed topic vocabulary cannot be located → tag/slug hygiene is skipped (not guessed) and the run notes it.
- Two consecutive runs in the same window → idempotency stamps make the second a no-op.
- The knowledge-vault repo has no changes to propose for the week → no empty PR is opened.
- Concurrency: a new scheduled run while the previous is still active is forbidden (no overlap).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST run curation on a fixed schedule (a frequent cheap tier and an infrequent heavy tier) with no always-on process.
- **FR-002**: Every pass MUST read only a bounded slice of the KB (e.g. recent-N via `list_recent` plus targeted `recall`), never an unbounded full-vault scan. (`review_summary` is unavailable under `KNOWLEDGE_MODE=lite`.)
- **FR-003**: Every pass MUST enforce hard per-run caps — maximum notes touched, maximum tool calls/turns, and a wall-clock deadline — and stop gracefully when any cap is hit, deferring remaining work.
- **FR-004**: The daily tier MUST support a dry-run mode that mutates nothing and a config-gated apply-mode that performs only additive, reversible metadata changes (scope, tags, confidence, `_*-candidate` tags).
- **FR-005**: No pass MUST ever perform a destructive delete directly against the KB; the maximum autonomous lossy action is applying a reversible `_*-candidate` tag.
- **FR-006**: Passes MUST be idempotent within a cycle via `curated:<tier>:<date>` stamps so re-runs are no-ops on already-processed notes.
- **FR-007**: The weekly tier MUST propose every lossy or editorial change (merges, tag/slug renames, rollups, archive candidates) as a single PR against the knowledge-vault repository and MUST NOT auto-merge it.
- **FR-008**: The weekly tier MUST surface genuine conflicts for human resolution (both note ids + claims) and MUST NOT auto-resolve them.
- **FR-009**: Tag and topic-slug hygiene MUST validate against the closed topic vocabulary; off-vocabulary values MUST be routed to review, never invented.
- **FR-010**: The system MUST run in-cluster on the existing `agent-runner` image, reusing the `claude-credentials` PVC, the `agents-kb-bearer` secret, and the in-cluster `knowledge-api` endpoint, following the `refresh-ping`/`kb-install` CronJob conventions.
- **FR-011**: The system MUST use a cost-frugal model tier per pass (cheap model for daily triage, stronger model for weekly consolidation) and forbid overlapping runs.
- **FR-012**: Failures (unreachable KB, missing credentials, failed tool calls) MUST surface in Job logs without corrupting KB state.

### Key Entities

- **Curation pass**: a scheduled, bounded unit of work (tier + cadence + budget + toolset + apply-posture).
- **Triage classification**: one of keep+scope / discard / dup / needs-review applied to a recent note.
- **Candidate tag**: a reversible marker (`_discard-candidate`, `_dup-candidate`, `_archive-candidate`, `_needs-review`) representing a proposed-but-unconfirmed lossy action.
- **Consolidation PR**: the single weekly pull request against knowledge-vault bundling all lossy/editorial proposals.
- **Idempotency stamp**: `curated:<tier>:<date>` tag marking a note as processed this cycle.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A daily run completes within its hard wall-clock deadline and never exceeds its configured note/turn caps (verifiable from Job duration + logs).
- **SC-002**: In dry-run, a daily run performs zero KB writes (verifiable: no note changes after the run).
- **SC-003**: In apply-mode, a daily run changes only additive metadata and performs zero deletions; a re-run within the same day is a no-op.
- **SC-004**: A weekly run results in exactly one (or zero, if nothing to propose) knowledge-vault PR and zero auto-merges and zero direct KB deletions.
- **SC-005**: Standing compute attributable to curation between scheduled runs is zero (no long-running process), and per-run compute is bounded by the model tier + caps.
- **SC-006**: Over a multi-week period, untriaged inbox notes trend down and near-duplicate/stale counts do not grow unbounded.

## Assumptions

- The knowledge-vault PR uses the existing agents GitHub credential mechanism (App installation token / deploy key) already available to in-cluster jobs; exact wiring is a planning detail.
- The staleness threshold for decay/archive defaults to a conservative window (e.g. ~180 days unused) and is configurable.
- Claude/Codex parity (Constitution III) does not require a Codex twin here: this is a scheduled infra job invoking one CLI headlessly, not an agent-facing skill/hook/command surface. If a Codex-driven variant is later desired it is a separate change.

## Resolved Decisions

- **Lite mode has no note-update tool** *(resolved 2026-06-24)*: Under `KNOWLEDGE_MODE=lite` the exposed MCP tools can READ (`recall`, `list_recent`, `get_note`, `find_conflicts`, `relations`) and CREATE (`ingest_note`, `capture_*`) notes, but there is **no tool to mutate an existing note's scope/tags/confidence** (that was an admin/governance capability). **Decision**: Tier-1 apply-mode (US2) therefore does NOT re-scope/retag existing notes over MCP; its only additive write is persisting a single dated triage **digest note** (`agent:kb-curator`, tag `curated:triage:<date>`) for queryability — still zero mutation of existing notes, zero deletes. Real existing-note metadata changes (re-scope, retag, merge, supersede, decay) are delivered by **Tier-2 editing the note markdown frontmatter in the knowledge-vault git repo via a reviewed PR**, which the `knowledge-ingest-worker` reconciles back into the KB. This keeps FR-004/FR-005 intact (additive, reversible, human-gated) and routes all mutation through git review.
- **Closed topic vocabulary source** *(resolved 2026-06-24)*: The vocabulary is no longer a `topics.yaml` file — it lives in the knowledge-api Postgres database (migrations `V2__topic_vocabulary.sql`, `V3__seed_topics.sql`, `V7__project_vocabulary.sql`; `domain/Topic.kt`). Its management surface is `AdminMcpTools`, a curator-governance tool that `KNOWLEDGE_MODE=lite` drops. Therefore the weekly pass cannot authoritatively validate against the closed vocabulary over MCP today. **Decision**: under lite mode, tag/slug hygiene (FR-009) is limited to consolidating *observed in-use* tags/slugs from the bounded result set and routing anything uncertain to `_needs-review`; full canonicalization against the authoritative vocabulary is deferred until knowledge-api exposes a read-only vocabulary tool in lite mode (a separate knowledge-api change). This keeps FR-009's "never invent" guarantee intact.
