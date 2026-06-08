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
- ⛔ **Blocked until #620 merges + App perms approved + token re-minted:** pushing `.github/workflows/*` (so the `Pipeline Complete` pipeline can't be added yet, and personal-stack's ruleset already requires that check → no PR merges).

## Org repos (all under ExtraToast, created, README-only except repo-template)

repo-template · gradle-conventions · github-workflows · kotlin-spring-commons ·
vue-web-commons · openapi-client-gradle · api-contract-checks · agent-kit ·
stalwart-provisioner · platform-blueprints · deploy-config-schema · authz-model
