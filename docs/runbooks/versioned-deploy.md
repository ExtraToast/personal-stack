# Versioned Deploy Model

Production deploys are driven by explicit version pins in Git, not by pushes to
`main` or mutable `:latest` tags.

## Target Model

- `main` is the integration branch. Merging to `main` does not by itself define
  the production version.
- release-please creates release tags in the form `vX.Y.Z`.
- `build-and-publish` publishes in-house container images with version tags that
  match the release tag.
- A release PR bumps explicit image tags under `platform/cluster/flux/**` from
  the previous version to the new version.
- Flux deploys the commit that pins those image tags.
- Rollback is a Git revert of the release PR that bumped the image tags.

## Deploying a Version

To deploy a specific release, reconcile the commit that pins the desired
`vX.Y.Z` image tags in `platform/cluster/flux/**`. The deployed state is the
Flux-applied Git state, so the image tags in the cluster manifests are the
source of truth.

## Rollback

Rollback is performed by reverting the tag-bump commit or PR. Flux reconciles
the reverted manifests and returns the affected workloads to the previously
pinned image versions.

## Keel Removal Sequencing

Keel-based `:latest` auto-rolls are removed from the in-house app deploy path in
a separate, sequenced PR. That Flux/Keel change must land only after the first
versioned in-house images have been published, so every workload can be pinned
to an existing version tag before `:latest` automation is removed.
