# Versioning & deploy model (corrected 2026-06-08)

> **This supersedes the earlier "tag → release, Flux-pinned, drop Keel" model.**
> personal-stack is **continuously deployed** and is NOT version-pinned. SemVer
> versioning applies to the **sub-packages** it consumes.

## personal-stack (the platform) — continuous auto-deploy, unchanged

- `main` is the deploy. Flux reconciles manifests from `main`; **Keel** auto-rolls
  in-house `:latest` images. **Keep both.**
- personal-stack itself is **not** released, tagged, or version-pinned. No
  release-please, no `vX.Y.Z` deploy, no Flux image-tag pinning, no manual
  "reconcile a tag" deploy. (The earlier #623 attempt at this was reverted in
  #624.)
- Deploys stay **automatic**: merge to `main` → image rebuild → Keel auto-roll.

## Sub-packages — SemVer-versioned and consumed via Renovate

The things personal-stack *depends on* are versioned; the platform pins and
upgrades them, and each upgrade flows through the normal auto-deploy.

What gets versioned:
1. **Extracted shared libraries/tooling** — `gradle-conventions`,
   `kotlin-spring-commons` (per-module), `vue-web-commons`, `openapi-client-gradle`,
   `api-contract-checks`, `agent-kit`, `stalwart-provisioner`. Each is released
   with release-please (`vX.Y.Z`) and published to GitHub Packages
   (`dev.extratoast.*` Maven / `@extratoast/*` npm) or GHCR.
2. **Reusable GitHub workflows** — `ExtraToast/github-workflows`, pinned by
   release tag + digest.
3. **API/frontend pair repos** (future, milestone **M7**, issue #625) — each
   pair split into its own repo, independently versioned, consumed as a pinned
   image version.

How personal-stack consumes them (exact pins, no ranges):
- **Gradle**: versions only in `gradle/libs.versions.toml`.
- **npm**: exact pins in the manifest.
- **Actions/workflows**: pinned `uses: …@vX.Y.Z`.
- **Pair-repo images**: pinned image version in the Flux manifest.
- **Renovate** (`renovate.json`, kept) opens exact-version bump PRs (ExtraToast
  artifacts grouped). A bump PR passes `Pipeline Complete`, merges, and the
  normal Keel/Flux auto-roll deploys it. **Renovate is the upgrade mechanism;
  deploy stays automatic.**

## Where SemVer/release tooling lives

Inside each **sub-package repo** (bootstrapped from `repo-template`, which carries
release-please + `ci.yml` + the ruleset). personal-stack only *consumes* — it
carries `renovate.json` and the version catalog, nothing else version-related.

## Net effect of the correction

| | Earlier (wrong) | Corrected |
| --- | --- | --- |
| personal-stack deploy | tag → Flux-pinned, manual | **continuous, Flux+Keel auto** |
| Keel | removed | **kept** |
| release-please on personal-stack | yes | **no** (reverted, #624) |
| Versioned units | personal-stack images | **sub-packages** (libs/tooling, workflows, pair repos) |
| Renovate | pin shared deps | **same — kept** |
