# Tracking issues & milestones (mirror)

Created in `ExtraToast/personal-stack`. Mirrored here because the automation
token can **create** issues but cannot **edit or comment** them, so this file is
the maintainable source for status — update it in PRs, and update the GitHub
issues out-of-band (operator) when possible.

## Milestones

| # | Title | Scope |
|---|---|---|
| 2 | M0 Foundations & conventions | repo-template, ruleset, Pipeline Complete CI, tag→release versioning, version catalog + Renovate. Unblocks all. |
| 3 | M1 Shared build tooling | gradle-conventions + github-workflows; personal-stack adopts. |
| 4 | M2 Kotlin/Spring commons | kotlin-spring-commons (modular) + reconcile + adopt. |
| 5 | M3 Vue web commons | vue-web-commons (npm) + adopt. |
| 6 | M4 OpenAPI, agent-kit, Stalwart | openapi tooling + agent-kit + stalwart-provisioner + adopt. |
| 7 | M5 Platform blueprints | homelab-platform-blueprints + adopt. |
| 8 | M6 Deploy schema & authz (design) | deploy-config-schema + authz-model design. |

## Epic

- **#605** — Epic: extract shared code into ExtraToast repos + version-pinned deploys. Holds the scope decisions + cross-cutting requirements. All issues below carry `Part of #605`.

## Tracking issues

| # | Milestone | Title / objective |
|---|---|---|
| #606 | M0 | **Enable workflows permission end-to-end.** Minter must request `workflows`+`issues`(+`packages:read`); App perms approved; re-mint. **Addressed by PR #620.** |
| #607 | M0 | **repo-template + apply common ruleset to all repos.** Merge repo-template#1; operator applies ruleset (needs admin — operator-owned); confirm `Pipeline Complete` required everywhere. |
| #608 | M0 | **Consolidate personal-stack CI into one `Pipeline Complete` pipeline.** Fold fast/full/contract-validate/migration-guard/vault-bootstrap-validate into one PR-triggered `ci.yml` ending in a `Pipeline Complete` aggregator. Blocked by #606. |
| #609 | M0 | **tag→release + Flux version-pinning, drop Keel `:latest`.** release-please tags vX.Y.Z; version-tagged images; release PR bumps explicit image tags in `platform/cluster/flux/**`; remove `keel.sh/policy: force` + `:latest`. |
| #610 | M0 | **Central version catalog + Renovate.** `gradle/libs.versions.toml` (`dev.extratoast.*`) + pinned npm manifest + Renovate (group ExtraToast artifacts, pin, gate on Pipeline Complete). |
| #611 | M1 | **Extract gradle-conventions + adopt.** Plugins + stable IDs + publish; personal-stack swaps `includeBuild build-logic` for pinned plugin coords; GH Packages creds in pluginManagement + dependencyResolutionManagement. |
| #612 | M1 | **Extract github-workflows + adopt.** Move actions + reusable workflows (deltas → inputs); personal-stack consumes pinned refs; Pipeline Complete built from shared reusable workflows. |
| #613 | M2 | **Extract kotlin-spring-commons + reconcile + adopt.** Modular publish; reconcile website vault + typed CommandBus; account for the two CommandBusConfig copies + 3 direct result-handler call sites; Dockerfiles resolve packages instead of copying libs. |
| #614 | M3 | **Extract vue-web-commons + adopt.** Package properly (publishConfig, peerDeps); UIs swap workspace dep for `@extratoast/vue-web-commons`; update imports, theme CSS, pnpm workspace, Dockerfiles. |
| #615 | M4 | **OpenAPI: openapi-client-gradle + api-contract-checks + adopt.** Split external client gen from contract-drift checks; update export tasks, assistant-ui scripts, package.json, contract-validate. |
| #616 | M4 | **Extract agent-kit + adopt.** Build from platform/agents/kit + council; preserve renderer flags + knowledge-api installer-serving; CI fails on drift of .claude/.codex/.agents/installer resource/runner entrypoint. |
| #617 | M4 | **Extract stalwart-provisioner + adopt.** Move Dockerfile/apply/bootstrap/accounts/plan templates/schema/validate; v2 `passwordRef` schema; keep domain accounts + DNS local. |
| #618 | M5 | **Extract homelab-platform-blueprints + adopt.** Generic module/script subset as flake input; host data/secrets/jobs/render outputs stay local. |
| #619 | M6 | **Design deploy-config-schema + authz-model.** Design-only; run after platform boundaries proven. |

## Consumer-rewiring conflict nodes (serialize per repo)

`settings.gradle.kts`, `gradle.properties`, root `package.json`/lockfiles, Dockerfiles, workflows, and service build files are first-class conflict nodes — sequence consumer-rewiring PRs so they don't collide.
