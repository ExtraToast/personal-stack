# Shared-code extraction — handoff dossier

This directory is a complete handoff for the program that extracts code shared
between **personal-stack** and **website** into standalone **ExtraToast** repos,
and re-bases personal-stack onto version-pinned dependencies + version-pinned
deploys. It exists so another agent/session can resume without re-discovering
anything. **personal-stack is migrated first; website comes later.**

## Read in this order

1. `council-brief.md` — the original problem statement + the operator's scope decisions.
2. `council-consolidated-plan.md` — the council's motivated repo list, classification, and ordering (the core research deliverable).
3. `council-tasks.json` — the 24-task / 6-wave extraction DAG (machine-readable).
4. `PLAN.md` — synthesis: repos, distribution, ownership, and how the operator's added requirements layer on top.
4a. `REPO-TEMPLATE.md` — the operator-added `repo-template` repo: full contents + the "what else to include" decisions + how every repo uses it.
5. `ISSUES.md` — the epic + every tracking issue (numbers, objectives, work, deps, milestone) — mirrored here because the automation token cannot edit/comment issues.
6. `VERSIONING.md` — the tag→release + Flux-pinned deploy model and the dependency-pinning model.
7. `CI-PIPELINE-COMPLETE.md` — the one-pipeline-per-repo `Pipeline Complete` contract + how to consolidate personal-stack's CI.
8. `ENVIRONMENT-AND-BLOCKERS.md` — GitHub App permissions, the token minter, push auth, and tooling gotchas. **Read before doing GitHub work.**
9. `NEXT-STEPS.md` — the exact ordered actions to resume.

## Status at handoff (2026-06-08)

- ✅ Council research + plan complete (ran on Codex; $0 Claude cost).
- ✅ `ExtraToast/repo-template` built — PR `ExtraToast/repo-template#1` (CI/release workflows **staged** under `.github/workflows-pending/` pending the token fix).
- ✅ personal-stack plan-of-record: milestones **M0–M6** (#2–#8), epic **#605**, tracking issues **#606–#619**.
- ✅ Minter fix PR **#620** (`Closes #606`) — widens minted-token permissions to include `workflows`/`issues`/`packages:read`.
- ⛔ ~~Blocked until #620 merges + App perms approved + token re-minted~~ — **RESOLVED, see update below.**

## MODEL CORRECTION — 2026-06-08 (versioning/deploy) — authoritative

The operator corrected the deploy model: **personal-stack stays continuously deployed (Flux + Keel `:latest` auto-roll) and is NOT version-pinned.** SemVer versioning + Renovate pinning apply to the **sub-packages** it consumes — extracted libs/tooling, reusable workflows, and the future **API/frontend pair repos** (each pair split into its own versioned repo; milestone **M7**, issue **#625**). The earlier "tag→release, Flux-pinned, drop Keel" work (#623) was **reverted in #624**; **Keel is kept**; `renovate.json` is retained. See the rewritten **`VERSIONING.md`** for the authoritative model. M0 = #608 (done) + Renovate (kept); #609 reduces to the revert; #610 catalog adoption waits on M1.

## STATUS UPDATE — 2026-06-08, post-unblock (supersedes the two corrections below)

- ✅ **Workflows unblocked.** PR **#620 is merged** and assistant-api rolled it out; the minter now grants `workflows`/`issues`/`packages:read`. repo-template's workflows are activated and **`ExtraToast/repo-template#1` is merged** — `Pipeline Complete` ran green there.
- ✅ **Issue editing works** with a correctly-scoped fresh token (epic #605 checklist was edited via the API). **Correction:** earlier text saying "the token cannot edit/comment issues" was due to using a stale / wrong-repo-scoped token. **The minter issues SINGLE-REPO tokens** — mint one per target repo: `POST $GITHUB_APP_TOKEN_URL {"repoUrl":"https://github.com/ExtraToast/<repo>.git"}`. A token minted for repo A returns 403 on repo B. Prefer minting fresh per repo over the session `GH_TOKEN`.
- ✅ **personal-stack was NOT actually merge-blocked.** **Correction:** `full.yml` already contains a job named `Pipeline Complete` that aggregates its gating jobs, and the ruleset matches on JOB name — so the required check has existed all along (that is how #620 merged). Earlier "no PR merges" text compared workflow names, not job names, and was wrong.
- ▶ **Remaining #608 work** is therefore narrower: `contract-validate`, `migration-guard`, `vault-bootstrap-validate` are SEPARATE PR workflows that do NOT feed `full.yml`'s aggregator, so they don't gate merges. Fold them into the `Pipeline Complete` aggregator (convert to `workflow_call`, call from `full.yml` gated by `detect-changes`, add to the aggregator's `needs`) — a careful path-gating refactor, verify via CI.
- ▶ **#610 is partly premature:** there are no `dev.extratoast.*` / `@extratoast/*` artifacts to pin until M1 publishes them; only the catalog/Renovate scaffolding can be added now.

## Org repos (all under ExtraToast, created, README-only except repo-template)

repo-template · gradle-conventions · github-workflows · kotlin-spring-commons ·
vue-web-commons · openapi-client-gradle · api-contract-checks · agent-kit ·
stalwart-provisioner · platform-blueprints · deploy-config-schema · authz-model
