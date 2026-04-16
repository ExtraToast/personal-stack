# Cluster Bootstrap

This directory will hold first-boot cluster artifacts that are applied before
Flux owns ongoing reconciliation.

Initial scope:

- bootstrap notes for the first `k3s` control-plane node
- install playbooks for clean host bring-up
- `Tailscale` tailnet bootstrap notes for new nodes
- manual Vault unseal runbooks
- first Flux bootstrap command references
- stateful cutover and recovery playbooks

## Home Install Wave

The clean-install sequence for `enschede-t1000-1` and the Raspberry Pi worker
wave lives in
[home-install-playbook.md](/Users/j.w.jonkers/IDEAProjects/personal-stack-2/platform/cluster/bootstrap/home-install-playbook.md:1).

The `Tailscale` tailnet bootstrap sequence for new hosts lives in
[tailscale-tailnet-playbook.md](/Users/j.w.jonkers/IDEAProjects/personal-stack-2/platform/cluster/bootstrap/tailscale-tailnet-playbook.md:1).

The actual home service cutover for media data and Samba lives in
[home-service-cutover-playbook.md](/Users/j.w.jonkers/IDEAProjects/personal-stack-2/platform/cluster/bootstrap/home-service-cutover-playbook.md:1).

## Vault unseal

The first `Vault` start on a fresh cluster is manual by design.

1. Wait for the `vault-0` pod in `data-system` to be running.
2. Initialize once:
   `kubectl -n data-system exec -it vault-0 -- vault operator init`
3. Store the unseal keys offline and store the initial root token outside the repo.
4. Unseal with quorum:
   `kubectl -n data-system exec -it vault-0 -- vault operator unseal`
5. Repeat `vault operator unseal` until the server reports `Sealed false`.

## Vault Kubernetes Auth Bootstrap

The Flux-managed `vault-bootstrap-auth` job configures the Kubernetes auth
backend, seeds a sample `kvv2/platform/sample` secret, and creates the
`platform-sample` role used by the injector-based smoke workload.

Prerequisites:

1. Create the bootstrap token secret from the initial root token:
   `kubectl -n data-system create secret generic vault-bootstrap-token --from-literal=token=...`
2. Confirm `vault-bootstrap-auth` completes successfully.
3. Check the `sample-reader` pod for `/vault/secrets/sample.txt`.
4. Delete `vault-bootstrap-token` after the bootstrap job has succeeded.

## k3s Worker Join Bootstrap

The first worker wave uses `frankfurt-contabo-1` as the bootstrap control
plane.

After a worker host has been clean-installed into `NixOS` and is reachable on
its steady-state SSH endpoint, copy the cluster join token onto the worker
before the first steady-state deploy:

1. `platform/scripts/bootstrap/bootstrap-k3s-worker.sh <node-name>`
2. `platform/scripts/deploy/deploy-host.sh <node-name>`
3. `kubectl get nodes -o wide`

The token copy path is inventory-driven through
[fleet.yaml](/Users/j.w.jonkers/IDEAProjects/personal-stack-2/platform/inventory/fleet.yaml:1)
so the bootstrap control plane node, API endpoint, and token file locations stay
in one place.

## Data Service Cutover And Recovery

Phase 9 recovery procedures for `PostgreSQL`, `RabbitMQ`, and `Valkey` live in
[data-services-playbook.md](/Users/j.w.jonkers/IDEAProjects/personal-stack-2/platform/cluster/bootstrap/data-services-playbook.md:1).

Use that playbook before the first stateful cutover window and keep the
recorded backup artifact names with the change record until rollback is no
longer needed.
