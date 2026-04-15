# Platform Rewrite Plan

This branch resets the platform direction around:

- `NixOS` for node operating system and host-native services
- `k3s` for cluster scheduling
- `Flux` for GitOps reconciliation
- `Helm` and `Kustomize` for workload packaging
- `Vault` for secrets and rotation

The purpose of this folder is to keep the rewrite explicit and to avoid repeating the mistake of building a large custom orchestration layer that duplicates the platform itself.

## What Is Carried Over From `multi-node-home`

Only the parts that are still useful under the new architecture should survive:

- The actual site and node inventory
- The current service set and rough placement intent
- The home-node utility requirements: `AdGuard`, `Samba`, `Tailscale`, media disk mounts, LAN access
- The current ingress and domain expectations
- The current Nomad jobs and templates as migration reference material only

The carried-over seed data from the branch now lives in [fleet.yaml](/Users/j.w.jonkers/IDEAProjects/personal-stack-2/platform/inventory/fleet.yaml:1).

## What Is Explicitly Not Carried Over

These branch changes should not be brought forward as part of the new platform:

- `ops/cluster` Kotlin renderers, rollout planners, remote runners, and tag-refresh logic
- Nomad-specific templating abstractions and service catalogs
- Consul and Nomad bootstrap/update logic
- GitHub workflows whose only purpose is driving the Nomad rollout model

Those pieces solved problems created by the previous stack. Recreating them on top of Kubernetes would repeat the same mistake.

## Target Architecture

### 1. Operating System

Every node runs `NixOS`.

- Host configuration is declared in a flake
- Disks are declared with `disko`
- Initial installs use `nixos-anywhere`
- Ongoing OS updates use `deploy-rs`
- Home utility services that are fundamentally node-local stay host-native

### 2. Cluster Topology

Run one Kubernetes cluster across the estate, but do not stretch the control-plane quorum across the WAN.

- `frankfurt` is the control-plane site
- `enschede` is a worker and utility site
- Phase 1 starts with the existing `frankfurt-contabo-1` node as a single control-plane node
- Phase 2 expands to three control-plane nodes in `frankfurt` for real HA
- Home nodes join only as workers and utility hosts
- The next home install wave is `enschede-home-t1000-1` first, then `enschede-pi-1`, then `enschede-pi-2` and `enschede-pi-3`

This avoids unstable `etcd` quorum behavior across `frankfurt <-> enschede`.

### 3. Workload Split

Use the platform where it fits best instead of forcing everything into one runtime.

- `k3s`: public apps, internal apps, observability, data services, media stack where containerized scheduling helps
- `NixOS host-native`: `AdGuard`, `Samba`, `Tailscale`, and bootstrap-critical services such as `Headscale`
- `Vault`: runs in-cluster on `frankfurt` storage, with manual unseal and Raft storage

### 4. Networking and Exposure

Use one exposure model and make LAN-direct access opt-in.

- Public ingress goes through a Flux-managed ingress controller
- Wildcard certificates are issued through `cert-manager`
- DNS records are managed through `external-dns`
- Home-LAN direct access uses `MetalLB` and reserved local IPs
- Services opt into one of: `public`, `public+lan`, `internal-only`, `lan-only`

### 5. Scheduling and Placement

Do not invent a separate placement DSL for workloads. Use Kubernetes primitives.

- Node labels express `site`, `arch`, `gpu vendor`, `gpu model`, `storage`, and `utility` traits
- Workloads use `nodeAffinity`, `topologySpreadConstraints`, `tolerations`, `resource requests`, and `StatefulSet` where needed
- GPU scheduling uses the Kubernetes device-plugin model
- `Jellyfin` targets the `t1000` node when it exists, and can temporarily use the `gtx960m`

### 6. Secrets

`Vault` remains the secret authority.

- Bootstrap secrets stay outside the repo
- Vault unseal keys remain offline and manual
- Kubernetes uses Vault auth for workloads
- First-party apps prefer file-based or sidecar-based secret delivery
- Third-party charts that insist on Kubernetes `Secret` objects get a narrow compatibility path

## Recommended Repo Shape

The new structure should live under `platform/`, not inside `infra/nomad` and not inside a deep custom orchestration tree.

```text
platform/
  README.md
  flake.nix
  inventory/
    fleet.yaml
  nix/
    modules/
      base/
      roles/
      services/
    profiles/
      control-plane.nix
      worker.nix
      utility.nix
      gpu-nvidia.nix
    hosts/
      frankfurt-contabo-1/
        default.nix
        disko.nix
      enschede-home-gtx960m-1/
        default.nix
        disko.nix
  cluster/
    bootstrap/
    flux/
      clusters/
        production/
      apps/
        core/
        data/
        mail/
        observability/
        media/
        utility/
  scripts/
    install/
    deploy/
    validate/
```

## Implementation Plan

### Phase 0: Freeze the Source of Truth

Goal: stop the architecture from drifting while the rewrite begins.

- Keep `infra/nomad` as the migration reference for current behavior
- Do not add more orchestration features to `ops/cluster`
- Treat [fleet.yaml](/Users/j.w.jonkers/IDEAProjects/personal-stack-2/platform/inventory/fleet.yaml:1) as the seed inventory for the rewrite
- Record missing information directly in the inventory instead of spreading it through scripts

Exit criteria:

- Every current node is represented
- Every current service is classified as `kubernetes` or `host-native`
- Every service has a target site and exposure intent

### Phase 1: Scaffold the New Platform Repository

Goal: create the minimal permanent structure without deploying anything yet.

- Add `flake.nix` for the platform
- Add `nix/hosts`, `nix/modules`, and `nix/profiles`
- Add `cluster/flux` and `cluster/bootstrap`
- Add validation commands:
  - `nix flake check`
  - `kustomize build`
  - `helm template`
  - `kubeconform`
- Add CI that validates render output only

Exit criteria:

- The repo can render NixOS host configs
- The repo can render cluster manifests
- CI validates both without touching real machines

### Phase 2: Build the NixOS Base Layer

Goal: make node installation and node updates boring.

- Use `nixos-anywhere` for first install
- Use `disko` for disk layout
- Use `deploy-rs` for updates
- Define shared base modules for:
  - SSH
  - firewall
  - users
  - journald
  - tailscale
  - container runtime
  - k3s prerequisites
- Define role modules for:
  - control-plane
  - worker
  - utility-host
  - nvidia
- Translate the current `infra/home-node` intent into NixOS modules instead of shell scripts

Exit criteria:

- A fresh VPS can be installed into NixOS from the flake
- A fresh home node can be installed into NixOS from the flake
- Existing host-local behavior for `AdGuard`, `Samba`, mounts, and `Tailscale` is representable declaratively

### Phase 3: Bootstrap Critical Host-Native Services

Goal: stand up the services that the rest of the platform depends on.

- Run `Headscale` host-native on the Frankfurt node first
- Run `Tailscale` on every node
- Keep `AdGuard` and `Samba` host-native on the home utility node
- Declare media disks and mountpoints through `disko` or host storage modules
- Add `NVIDIA` driver support only on the nodes that need it

Exit criteria:

- Nodes in both sites can see each other over the chosen network path
- Home LAN services work without Kubernetes
- GPU nodes boot with the required driver stack

### Phase 4: Stand Up the First k3s Control Plane

Goal: get the cluster online with the smallest viable topology.

- Install `k3s` on `frankfurt-contabo-1`
- Disable bundled components that should be GitOps-managed separately
- Join `enschede-home-gtx960m-1` as a worker once cross-site connectivity is ready
- Label nodes by:
  - `site`
  - `arch`
  - `utility`
  - `gpu vendor`
  - `gpu model`
  - `storage class`

Exit criteria:

- One control-plane and one worker are live
- Cross-site scheduling works
- Labels and taints express the intended placement model

### Phase 5: Install Flux and Cluster Base Services

Goal: stop doing imperative cluster changes.

- Bootstrap `Flux`
- Add a cluster base layer for:
  - ingress controller
  - `cert-manager`
  - `external-dns`
  - `MetalLB`
  - `metrics-server`
- Keep all cluster add-ons managed through `Flux`, not through ad-hoc `kubectl`

Exit criteria:

- A Git commit can reconcile cluster add-ons
- Public DNS and wildcard certificates work
- A home-only service can receive a LAN IP through `MetalLB`

### Phase 6: Bring Up Vault

Goal: re-establish secret management before application migration.

- Install `Vault` through Helm in `frankfurt`
- Use integrated Raft storage on persistent volumes
- Keep manual unseal
- Configure Kubernetes auth
- Define the secret delivery policy:
  - first-party apps use Vault-native delivery
  - compatibility paths exist for third-party charts

Exit criteria:

- Vault survives pod reschedule with persistent storage
- Manual unseal is documented and repeatable
- A sample workload can read a secret through Kubernetes auth

### Phase 7: Observability and Backup

Goal: get visibility before migrating application traffic.

- Migrate `Prometheus`, `Grafana`, `Loki`, `Tempo`, and `Alloy`
- Add cluster-level dashboards and alerts
- Add backup jobs for:
  - Vault
  - PostgreSQL
  - RabbitMQ definitions
  - important PVC snapshots where applicable

Exit criteria:

- Cluster, node, and workload metrics are visible
- Logs and traces are reachable
- Stateful backups exist before app cutover

### Phase 8: Migrate Stateless and Low-Risk Applications

Goal: move the easiest services first.

- Move `app-ui`, `auth-ui`, `assistant-ui`
- Move `auth-api`, `assistant-api`
- Move `flaresolverr`
- Move `uptime-kuma`
- Move `n8n` if its data and credentials path is ready

Exit criteria:

- Public ingress, auth, TLS, and rollout flow are proven on low-risk services
- Nomad remains the fallback only for services not yet migrated

### Phase 9: Migrate Data Services

Goal: move the stateful platform core deliberately.

- Migrate `PostgreSQL`
- Migrate `RabbitMQ`
- Migrate `Valkey`
- Define clear PVC, backup, restore, and rollback procedures for each

Exit criteria:

- Stateful cutover playbooks are written and rehearsed
- Every migrated datastore has a verified restore path

### Phase 10: Migrate Home Media

Goal: move the media estate without losing LAN simplicity.

- Run the media stack on home workers only
- Keep media storage node-local at first
- Expose selected services publicly and on LAN where required
- Install the `NVIDIA` Kubernetes device plugin on `gtx960m` and later `t1000`
- Move `Jellyfin` to the `t1000` node once provisioned

Exit criteria:

- `Jellyfin`, `Radarr`, `Sonarr`, and downloads run on home nodes
- LAN access works directly
- Public access works through ingress when enabled
- GPU scheduling is proven on the intended node

### Phase 11: Migrate Mail Through Kubernetes

Goal: keep mail under the same GitOps and reconciliation model as the rest of the platform where that does not compromise deliverability.

Preferred direction:

- run `Stalwart` as a `Flux`-managed Kubernetes workload pinned to `frankfurt`
- keep mail state on persistent cluster storage in `frankfurt`
- keep the web admin route behind forward-auth
- expose SMTP, IMAP, submission, and sieve through direct node-bound publishing and DNS, not through Traefik HTTP ingress
- keep `mail.jorisjonkers.dev` as the direct, non-proxied mail endpoint and `jorisjonkers.dev` MX target, matching [infra/dns/jorisjonkers.dev.zone](/Users/j.w.jonkers/IDEAProjects/personal-stack-2/infra/dns/jorisjonkers.dev.zone:1)
- defer HA mail until the core platform has a stable three-node Frankfurt control plane

Exit criteria:

- `Stalwart` is reconciled by `Flux`
- the admin UI is reachable through the public edge with auth in front
- mail protocols have an explicit Kubernetes exposure model and backup path

### Phase 12: Expand to HA and Retire Nomad

Goal: finish the cutover cleanly.

- Add two more Frankfurt control-plane nodes
- Move to a three-node control-plane quorum
- Repoint remaining workloads
- Remove Nomad and Consul from hosts once no longer needed
- Archive the Nomad-era scripts and docs

Exit criteria:

- No production workload depends on Nomad
- No node depends on Consul for service discovery
- The repo has one active platform model

## Migration Order by Service Family

Use this order unless a dependency forces an adjustment:

1. Host bootstrap and networking
2. Cluster add-ons and certificates
3. Vault
4. Observability
5. Stateless applications
6. Stateful application services
7. Media services
8. Mail
9. Nomad retirement

## Validation Strategy

Every phase should have a validation path before the next one begins.

- `nix flake check` for NixOS evaluation
- VM tests for critical NixOS modules where practical
- `kustomize build` and `helm template` for rendered manifests
- `kubeconform` for Kubernetes schema validation
- `Flux` dry-run or render validation in CI
- one smoke environment before production cutover of each family

## Immediate Next Steps

The next implementation steps should focus on real host bring-up and the first live migrations:

1. Use the committed bootstrap SSH metadata for `enschede-home-t1000-1`, clean-install it to `NixOS`, and validate GPU runtime plus `k3s` worker join
2. Use `enschede-pi-1` as the first ARM worker install rehearsal, then fold in `enschede-pi-2` and `enschede-pi-3` once the join path is boring
3. Move one low-risk workload family onto the new home nodes before widening the wave
4. Publish `Stalwart` mail protocols through the direct DNS model in [infra/dns/jorisjonkers.dev.zone](/Users/j.w.jonkers/IDEAProjects/personal-stack-2/infra/dns/jorisjonkers.dev.zone:1) instead of trying to route them through Traefik
5. Keep the admin UI on `stalwart.jorisjonkers.dev` behind forward-auth while the protocol endpoint stays on `mail.jorisjonkers.dev`

For clean machines, the expected sequence is:

1. Boot a temporary SSH-reachable installer environment and point the inventory `ssh` metadata at it
2. Run `platform/scripts/install/install-host.sh <node-name>` to install `NixOS` from the flake with `nixos-anywhere`
3. Reboot into the installed `NixOS` system and verify baseline host services such as networking, `Tailscale`, storage mounts, and GPU runtime where applicable
4. Use `platform/scripts/deploy/deploy-host.sh <node-name>` for steady-state updates through `deploy-rs`
5. Only after the node is healthy should it join the target `k3s` role and start receiving Flux-managed workloads
