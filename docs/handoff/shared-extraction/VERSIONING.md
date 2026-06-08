# Versioning & deploy model (operator-approved)

Replaces "main is the deploy" + Keel `:latest`. Two halves: how shared artifacts
are versioned/consumed, and how personal-stack itself is released/deployed.

## Shared artifacts (each ExtraToast repo)

- Released via **release-please**: squash-merged conventional-commit PRs → a release PR → merging it tags `vX.Y.Z`, writes `CHANGELOG.md`, bumps `.release-please-manifest.json`.
- The published-release event publishes the exact version: Maven → GitHub Packages `dev.extratoast.*`; npm → `@extratoast/*`; images → `ghcr.io/extratoast/<repo>/<image>:X.Y.Z`.
- Pre-1.0 uses minor as the breaking lever (`bump-minor-pre-major`).

## How personal-stack consumes shared deps (exact pins, no ranges)

- **Single source of truth:** `gradle/libs.versions.toml` for `dev.extratoast.*`; a pinned npm manifest for `@extratoast/*`. No `^`/`~`.
- **Renovate** opens exact-version bump PRs, grouping ExtraToast artifacts into one "platform bump"; every bump PR must pass `Pipeline Complete`.
- GitHub Actions / reusable workflows pinned to a release tag (digest via Renovate): `uses: ExtraToast/github-workflows/.github/workflows/<wf>.yml@vX.Y.Z`.

## How personal-stack is released & deployed (deploy a specific version)

Today: build-and-publish tags `:latest` + `:<sha>`; **Keel** polls `:latest` every 2 min and force-rolls; Flux reconciles `main`. Target model:

1. A git tag `vX.Y.Z` (release-please) builds images tagged with the **version** (keep `:<sha>` for traceability). No `:latest` for in-house images.
2. A **release PR bumps the EXPLICIT image tags** in `platform/cluster/flux/**` to that version.
3. **Deploying a version = reconciling the commit that pins it.** **Rollback = `git revert`** of the bump.
4. **Remove** `keel.sh/policy: force` + `keel.sh/match-tag` + `:latest` from in-house Deployments (issue #609). (Third-party images like `linuxserver/*`, `mariadb`, `ollama` are out of scope.)

This makes "which version is live" a reviewable, revertable fact in git, and makes deploying an arbitrary past version a one-line tag change.

## Files/areas to touch (personal-stack)

- `.github/workflows/build-and-publish.yml` — add version tag derived from the release tag; stop relying on `:latest` for in-house rollout.
- `platform/cluster/flux/apps/**/deployment*.yaml` + cronjobs/pods — replace `:latest` with the pinned version; drop Keel force annotations on in-house images (grep: `keel.sh/policy`, `ghcr.io/extratoast/personal-stack/*:latest`).
- New: release-please config + `gradle/libs.versions.toml` + Renovate config.
- `docs/` / runbook — document deploy = reconcile tag, rollback = revert.

Canonical reference for the model lives in `ExtraToast/repo-template/VERSIONING.md`.
