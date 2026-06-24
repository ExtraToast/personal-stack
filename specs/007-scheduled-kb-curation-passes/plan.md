# Implementation Plan: 007-scheduled-kb-curation-passes

**Branch**: `007-scheduled-kb-curation-passes` | **Date**: 2026-06-24 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/007-scheduled-kb-curation-passes/spec.md`

## Summary

Replace the retired always-on `knowledge-curator` with two scheduled, bounded, headless-Claude CronJobs. **Tier-1** (daily, Haiku) triages recent captures; it ships in dry-run and is promoted to a config-gated apply-mode that performs only additive, reversible metadata changes. **Tier-2** (weekly, Sonnet) consolidates the KB — dedup-merge, conflict surfacing, stale decay, rollups — and proposes every lossy/editorial change as a single branch/PR against the `knowledge-vault` git repo, never auto-merged and never a destructive KB delete. Both reuse the in-cluster `refresh-ping`/`kb-install` conventions and are hard-capped on turns, notes, and wall-clock so standing/per-run compute stays bounded.

## Technical Context

**Language/Version**: Bash (CronJob command) + headless `claude -p` (Claude Code CLI) prompts; YAML manifests (Kustomize).
**Primary Dependencies**: `agent-runner` image (`ghcr.io/extratoast/personal-stack/agent-runner:latest`); knowledge-api MCP (lite toolset); `gh`/`git`; Vault Secrets Operator.
**Storage**: knowledge-api Postgres (read/write via MCP only); `knowledge-vault` git repo (Tier-2 branch/PR).
**Testing**: `kustomize build platform/cluster/flux/apps/agents` + `bash -n` on embedded scripts + JSON validity of generated MCP config (no cluster from here; live verification documented in quickstart).
**Target Platform**: k3s, `agents-system` namespace, node `enschede-gtx-960m-1`.
**Project Type**: platform (Flux/k8s infra).
**Performance Goals**: per-run bounded — Tier-1 ≤24 turns / ≤25 notes / 10-min deadline; Tier-2 ≤ ~60 turns / ≤40 notes / 30-min deadline. Zero standing compute between runs.
**Constraints**: `KNOWLEDGE_MODE=lite` drops `review_summary`/admin tools → build on `list_recent`+`recall`+`find_conflicts`+`relations`+`ingest_note`. No destructive deletes by the agent. Lossy changes human-gated via one PR.
**Scale/Scope**: single shared KB; small daily/weekly batches.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] No attribution is introduced in files, comments, commit text, or PR text — manifests/prompts/commits for this feature carry none. (Note: PRs #723/#724 erroneously included trailers; corrected separately — see research.md risk R4.)
- [x] Claude/Codex parity is preserved — this is a scheduled infra Job invoking one CLI headlessly, not an agent-facing skill/hook/command surface, so no Codex twin is required (recorded in spec Assumptions).
- [x] Rendered artifacts: this feature touches only hand-authored Flux manifests under `platform/cluster/flux/apps/agents/`, not render-managed inventory/installer outputs. No renderer to run.
- [x] Small stacked PR boundary: docs (this spec set) + Tier-2 manifests + Tier-1 apply-mode enhancement, stacked on the Tier-1 dry-run PR (#724). Unrelated cleanup excluded.
- [x] Verification command identified: `kustomize build` + `bash -n` + JSON check (per touched manifest); live `kubectl create job --from=cronjob/...` documented for post-merge.

## Project Structure

### Documentation

```text
specs/007-scheduled-kb-curation-passes/
|-- spec.md
|-- plan.md
|-- research.md
|-- data-model.md
|-- quickstart.md
|-- checklists/requirements.md
`-- tasks.md
```

### Source Code

```text
platform/cluster/flux/apps/agents/
|-- kustomization.yaml                       # add kb-curator (Tier-1, via #724) — unchanged here
|-- kb-curator/
|   |-- kustomization.yaml                   # add weekly-consolidation.yaml
|   |-- cronjob.yaml                         # Tier-1 daily triage (exists, #724) — apply-mode enhancement
|   `-- weekly-consolidation.yaml            # NEW: Tier-2 weekly CronJob
`-- credentials/
    |-- kustomization.yaml                   # add knowledge-vault deploy-key VSS
    `-- knowledge-vault-deploy-key-vss.yaml  # NEW: inject knowledge-vault deploy key into agents-system
```

**Structure Decision**: Tier-2 runs in `agents-system` (consistent with Tier-1; reuses agent-runner image + `claude-credentials` PVC + `agents-kb-bearer`). knowledge-vault write access is via the existing ed25519 deploy key (Vault `secret/knowledge-system/vault-deploy-key`, repo `git@github.com:ExtraToast/knowledge-vault.git`), injected into `agents-system` through a new `VaultStaticSecret`. Tier-2 does a fresh ephemeral SSH clone (does NOT bind the `knowledge-vault-clone` RWO PVC owned by the ingest worker).

## Phase 0: Outline & Research

Resolved unknowns (full detail in `research.md`):

1. **knowledge-vault write mechanism** — ed25519 deploy key at Vault `secret/knowledge-system/vault-deploy-key`, SSH remote `git@github.com:ExtraToast/knowledge-vault.git` (per `knowledge-ingest-worker`). Deploy keys can push but cannot open PRs via API.
2. **PR creation** — Decision: Tier-2 pushes a `curator/weekly-<date>` branch via the deploy key and emits the GitHub compare URL to Job logs (one-click human PR open). Auto-opening via `gh` + agents-api installation-token is a documented follow-up, gated on confirming the GitHub App is installed on `knowledge-vault` (R2).
3. **Topic vocabulary** — now in knowledge-api Postgres (`V2/V3/V7` migrations), managed via `AdminMcpTools` which lite mode drops → Tier-2 limits tag/slug hygiene to observed in-use values, routes uncertain to `_needs-review` (spec Resolved Decisions).
4. **Claude auth in headless Job** — `claude-credentials` PVC (Tier-1 proven path) with the `~/.claude.json` restore step.

**Output**: `research.md`

## Phase 1: Design & Contracts

1. `data-model.md`: curation pass, triage classification, candidate tags, idempotency stamp, consolidation PR.
2. `contracts/`: the env/config contract for each CronJob (env vars, allowed-tools per mode, caps) and the Tier-2 PR contract (branch naming, PR body shape, never-auto-merge).
3. `quickstart.md`: how to dry-run, read logs, promote Tier-1 to apply-mode, trigger Tier-2 on demand, and live-verify.
4. Re-run Constitution Check (still PASS).

**Output**: `data-model.md`, `contracts/*`, `quickstart.md`

## Phase 2: Task Planning Approach

`/speckit.tasks` should produce ordered tasks grouped by user story (US1 already shipped via #724; US2 = Tier-1 apply-mode enhancement; US3 = Tier-2 manifests + deploy-key VSS + prompt), each independently testable with `kustomize build`/`bash -n`, plus a live-verification task. Keep Tier-2 manifest, the deploy-key VSS, and the kustomization wiring as separate small tasks to preserve revertability.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
| --- | --- | --- |
| None | — | — |

## Progress Tracking

**Phase Status**:

- [x] Phase 0: Research complete
- [x] Phase 1: Design complete
- [x] Phase 2: Task planning approach complete

**Gate Status**:

- [x] Initial Constitution Check: PASS
- [x] Post-Design Constitution Check: PASS
- [x] All NEEDS CLARIFICATION resolved
