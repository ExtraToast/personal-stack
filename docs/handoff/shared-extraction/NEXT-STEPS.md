# Next steps — resume here

> **2026-06-08 update — read README.md "STATUS UPDATE" first.** Step 0 below is
> DONE: #620 is merged, the minter grants workflows/issues, and
> `repo-template#1` is merged. Issues *can* be edited (mint a per-repo token).
> personal-stack already merges (full.yml has the `Pipeline Complete` job).
> Resume at the narrowed #608 (fold the 3 extra PR workflows into the
> aggregator), then #610 (after M1 publishes artifacts) and #609.

Ordered. Reference the tracking issue in every PR; keep `ISSUES.md` current and,
where possible, update the GitHub issues too (editing works with a fresh
per-repo token).

## 0. Clear the workflows-token blocker (issue #606 — PR #620) — ✅ DONE

1. ~~Merge **PR #620**~~ (merged; minter now requests `workflows`/`issues`/`packages:read`).
   - ⚠️ #620 itself can't merge until `Pipeline Complete` exists OR an operator
     merges it under the ruleset. Operator may need to merge #620 manually
     (chicken-and-egg: the fix that enables CI can't pass the CI gate yet).
2. Operator widens App repository permissions + approves on each installation;
   deploy assistant-api; drop the cached runner token (see ENVIRONMENT-AND-BLOCKERS.md).
3. Re-mint the session token; confirm a workflow push succeeds.

## 1. Activate CI + ruleset (issues #607, #608)

4. Move `repo-template`'s staged workflows into `.github/workflows/`
   (`git mv .github/workflows-pending/*`), push, merge repo-template#1.
5. Build personal-stack's consolidated `ci.yml` ending in `Pipeline Complete`
   (CI-PIPELINE-COMPLETE.md). Open PR → #608. Once green, personal-stack PRs can
   merge normally.
6. (Operator) Confirm the ruleset's required check resolves on personal-stack
   and is applied to the other ExtraToast repos.

## 2. Versioning foundation (issues #610, #609)

7. Add `gradle/libs.versions.toml` + pinned npm manifest + Renovate config (#610).
8. Implement tag→release (release-please), version-tagged images, Flux explicit
   tag pinning, and remove Keel `:latest`/force on in-house images (#609).
   See VERSIONING.md for exact files.

## 3. Extractions, in dependency order (council DAG: `council-tasks.json`)

9. **M1:** gradle-conventions (#611) → github-workflows (#612); personal-stack adopts (pinned plugin coords + reusable workflows).
10. **M2:** kotlin-spring-commons (#613) — reconcile divergences first; adopt per service module.
11. **M3:** vue-web-commons (#614).
12. **M4:** openapi tooling (#615), agent-kit (#616), stalwart-provisioner (#617).
13. **M5:** homelab-platform-blueprints (#618).
14. **M6:** design deploy-config-schema + authz-model (#619).

## Working rules

- **personal-stack only**; website is a later program.
- PRs: squash-only, linear history, impersonal commit/PR voice, **no Co-Authored-By / no "Claude" attribution** (repo convention in CLAUDE.md), assigned/labelled per CLAUDE.md.
- Each new ExtraToast repo is bootstrapped from `repo-template`; apply the common ruleset (operator) so `Pipeline Complete` gates it.
- Verify gradle changes by actually building (not in a codex sandbox) or via CI.
- Serialize consumer-rewiring PRs around the conflict-node files (ISSUES.md).
