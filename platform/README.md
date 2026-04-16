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
- The next home install wave is `enschede-t1000-1` first, then `enschede-pi-1`, then `enschede-pi-2` and `enschede-pi-3`

This avoids unstable `etcd` quorum behavior across `frankfurt <-> enschede`.

### 3. Workload Split

Use the platform where it fits best instead of forcing everything into one runtime.

- `k3s`: public apps, internal apps, observability, data services, media stack where containerized scheduling helps
- `NixOS host-native`: `AdGuard`, `Samba`, `Tailscale`, and other node-local utility services
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
      enschede-gtx-960m-1/
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

- Run `Tailscale` on every node
- Use the hosted `Tailscale` admin console for machine auth keys, tags, and policy management
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
- Join `enschede-gtx-960m-1` as a worker once cross-site connectivity is ready
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
- Keep `Jellyseerr` with the rest of the media apps instead of leaving it behind on the old home node
- Prove the WireGuard-based download path before the final home cutover

Exit criteria:

- `Jellyfin`, `Jellyseerr`, `Radarr`, `Sonarr`, `Bazarr`, and downloads run on home nodes
- LAN access works directly
- Public access works through ingress when enabled
- GPU scheduling is proven on the intended node
- The media VPN path is validated on the new node

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

1. Use the committed bootstrap SSH metadata for `enschede-t1000-1`, clean-install it to `NixOS`, join it to the `Tailscale` tailnet, and validate GPU runtime plus `k3s` worker join
   Use `platform/scripts/bootstrap/bootstrap-tailnet.sh <node-name>` after the
   first boot so the overlay join is explicit and repeatable.
   Then use `platform/scripts/bootstrap/bootstrap-k3s-worker.sh <node-name>` so
   the worker token copy is explicit and repeatable.
2. Use `enschede-pi-1` as the first ARM worker install rehearsal, then fold in `enschede-pi-2` and `enschede-pi-3` once the join path is boring
3. Move one low-risk workload family onto the new home nodes before widening the wave
4. Publish `Stalwart` mail protocols through the direct DNS model in [infra/dns/jorisjonkers.dev.zone](/Users/j.w.jonkers/IDEAProjects/personal-stack-2/infra/dns/jorisjonkers.dev.zone:1) instead of trying to route them through Traefik
5. Keep the admin UI on `stalwart.jorisjonkers.dev` behind forward-auth while the protocol endpoint stays on `mail.jorisjonkers.dev`

For clean machines, the expected sequence is:

1. Create exactly one local deploy key file per host in [platform/nix/authorized-keys/README.md](/Users/j.w.jonkers/IDEAProjects/personal-stack-2/platform/nix/authorized-keys/README.md:1)
   Use the node name as the filename, for example `platform/nix/authorized-keys/frankfurt-contabo-1.pub`.
   That host-specific file is what gets baked into post-install `deploy@<host>:2222` access.
   If the exact key you plan to use is missing there at install time, the new machine will boot successfully but reject your SSH access after the reboot.
2. Keep the inventory `ssh` metadata pointed at the desired `NixOS` end state: `deploy@<host>:2222`
3. For a first install on an existing machine, add `bootstrap_ssh` with whatever admin SSH endpoint the current OS already exposes today
4. Make sure that `bootstrap_ssh` user has SSH-key access and passwordless `sudo`; you do not need to move the old OS to `deploy@<host>:2222` before install
5. Run `platform/scripts/install/install-host.sh <node-name>` to install `NixOS` from the flake with `nixos-anywhere`
   If the current machine still needs explicit bootstrap auth, pass it directly at runtime with either
   `platform/scripts/install/install-host.sh --ssh-key ~/.ssh/ps-t1000 <node-name>`
   or
   `platform/scripts/install/install-host.sh --ssh-password '<bootstrap-password>' <node-name>`
6. Reboot into the installed `NixOS` system and verify base host health
7. Use a one-off `Tailscale` auth key from the admin console and run `platform/scripts/bootstrap/bootstrap-tailnet.sh <node-name>` so the node joins the shared private overlay
8. For worker nodes, run `platform/scripts/bootstrap/bootstrap-k3s-worker.sh <node-name>` so the join token is copied from the bootstrap control plane onto the new host
9. Use `platform/scripts/deploy/deploy-host.sh <node-name>` for steady-state config updates over `deploy@<host>:2222`
10. Only then let the node act as a real `k3s` worker/utility host and start receiving Flux-managed workloads

## Bring-up Log

Track the remaining platform cleanup here instead of rediscovering it during the
next host install.

- `enschede-t1000-1` reached a successful steady-state `deploy-host.sh` run on April 16, 2026.
- The `Samba` module still uses `services.samba.shares`, which now evaluates with a rename warning on current `NixOS`. Migrate it to `services.samba.settings` in [platform/nix/modules/services/samba.nix](/Users/j.w.jonkers/IDEAProjects/personal-stack-2/platform/nix/modules/services/samba.nix:1).
- The `k3s` worker path still evaluates with `k3s: token, tokenFile or configPath ... should be set if role is 'agent'` before `bootstrap-k3s-worker.sh` copies the join token onto a new node. Rework [platform/nix/modules/k3s/bootstrap.nix](/Users/j.w.jonkers/IDEAProjects/personal-stack-2/platform/nix/modules/k3s/bootstrap.nix:1) so agent nodes stop warning during evaluation while still keeping the token out of Git.
- `enschede-gtx-960m-1` is still not install-complete in Git. [platform/nix/hosts/enschede-gtx-960m-1/disko.nix](/Users/j.w.jonkers/IDEAProjects/personal-stack-2/platform/nix/hosts/enschede-gtx-960m-1/disko.nix:1) only mounts `/srv/media`; it still needs real `/boot` and `/` disk layout before full-flake validation can pass without deploy-time workarounds.
- The cluster API endpoint in [platform/inventory/fleet.yaml](/Users/j.w.jonkers/IDEAProjects/personal-stack-2/platform/inventory/fleet.yaml:1) is still pinned to the public Frankfurt IP. Once the first home workers are on the tailnet, switch that to the `Tailscale` address or MagicDNS name.
- `deploy-host.sh` currently falls back to `bootstrap_ssh.host` for `install-ready` nodes because pre-tailnet names like `enschede-t1000-1` are not resolvable from the workstation yet. After each node joins `Tailscale`, clean up inventory so steady-state deploys use the final private hostname path only.
- Remote builds currently print `warning: ignoring the client-specified setting 'store', because it is a restricted setting and you are not a trusted user`. This did not block deployment, but it is worth revisiting if the remote builder setup needs tightening later.
