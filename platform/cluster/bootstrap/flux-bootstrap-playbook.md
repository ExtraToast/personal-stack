# Flux Bootstrap Playbook

First-time install of Flux onto the cluster. Run once per cluster. After this,
any change to `main` under `platform/cluster/flux/` reconciles automatically
within ~1 minute.

## Why manual (not `flux bootstrap`)

`flux bootstrap github` pushes a commit containing its controller manifests
directly to the branch you bootstrap against. This repo's `main` branch has
protection rules that block direct pushes, which the CLI can't negotiate.
Instead the controller + sync manifests are already checked into the tree at:

- `platform/cluster/flux/clusters/production/flux-system/gotk-components.yaml`
- `platform/cluster/flux/clusters/production/flux-system/gotk-sync.yaml`
- `platform/cluster/flux/clusters/production/flux-system/kustomization.yaml`

They land via a normal PR. The one-time cluster-side install is `kubectl apply`
plus a secret.

## Prerequisites

- `flux` CLI locally (`brew install fluxcd/tap/flux`).
- Working kubeconfig against the target cluster (see Phase 4 in
  [../../../README.md](/Users/j.w.jonkers/IDEAProjects/personal-stack-2/platform/README.md:1)).
- A **fine-grained** GitHub PAT scoped only to this repository:
  - Repository access: only `ExtraToast/personal-stack`.
  - Permissions: `Contents: Read-only`, `Metadata: Read-only`.
    Read-only is sufficient — after bootstrap Flux only pulls.
  - The PAT is consumed once by `flux create secret git`. Delete it from
    GitHub afterwards or set a short expiry.

## One-time install on the cluster

```bash
git checkout main && git pull
export KUBECONFIG=~/.kube/personal-stack.yaml

# 1. Install the Flux controllers + GitRepository/Kustomization sync.
kubectl apply -k platform/cluster/flux/clusters/production/flux-system

# 2. Supply the read-only PAT so the GitRepository can pull the repo.
GITHUB_TOKEN=$(cat)                # paste token, Enter, Ctrl-D
flux create secret git flux-system \
  --url=https://github.com/ExtraToast/personal-stack \
  --username=ExtraToast \
  --password="$GITHUB_TOKEN"
unset GITHUB_TOKEN

# 3. Verify Flux is reconciling main.
flux check
flux get sources git -A
flux get kustomizations -A --watch   # Ctrl-C once everything is Ready=True
```

Expected end state: `flux get kustomizations -A` reports every kustomization
under `platform/cluster/flux/apps/**` as `Ready=True`.

## Upgrading Flux later

Regenerate the components file into a new PR:

```bash
flux install --export > platform/cluster/flux/clusters/production/flux-system/gotk-components.yaml
git checkout -b flux-upgrade-<version>
git add platform/cluster/flux/clusters/production/flux-system/gotk-components.yaml
git commit -m "flux: upgrade controllers to <version>"
git push -u origin flux-upgrade-<version>
```

Open the PR, merge, and Flux self-upgrades its controllers on the next
reconciliation tick. No `kubectl apply` needed for upgrades — Flux is
reconciling `flux-system/` from the repo like any other Kustomization.

## Rotating the GitHub secret

Delete the cluster-side secret and recreate it with a fresh PAT:

```bash
kubectl -n flux-system delete secret flux-system
GITHUB_TOKEN=$(cat)
flux create secret git flux-system \
  --url=https://github.com/ExtraToast/personal-stack \
  --username=ExtraToast \
  --password="$GITHUB_TOKEN"
unset GITHUB_TOKEN
flux reconcile source git flux-system
```

## Why the `flux-system` namespace is owned by `gotk-components.yaml`

Earlier iterations had a duplicate `Namespace/flux-system` in
`apps/core/namespace.yaml`. That tripped `kustomize build` with
`may not add resource with an already registered id`. The namespace now lives
only in `gotk-components.yaml`; don't re-add it to `apps/core`.
