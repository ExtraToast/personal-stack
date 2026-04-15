# Home Install Playbook

This playbook covers the first clean-install wave for the currently available
home nodes:

- `enschede-home-t1000-1`
- `enschede-pi-1`
- `enschede-pi-2`
- `enschede-pi-3`

The intended order is:

1. `enschede-home-t1000-1`
2. `enschede-pi-1`
3. `enschede-pi-2`
4. `enschede-pi-3`

## Expected Sequence

For each node, the sequence is:

1. Boot a temporary installer environment that exposes SSH.
2. Make the installer reachable at the `ssh.host` recorded in [fleet.yaml](/Users/j.w.jonkers/IDEAProjects/personal-stack-2/platform/inventory/fleet.yaml:1).
   The current default is `root@<node-name>:22`; swap the host value to a raw LAN IP if local name resolution is not ready yet.
3. Run:
   `platform/scripts/install/install-host.sh <node-name>`
4. Reboot into the installed `NixOS` system.
5. Validate the base host:
   `hostnamectl`
   `tailscale status`
   `systemctl status k3s`
6. Run:
   `platform/scripts/deploy/deploy-host.sh <node-name>`
7. Verify node registration and labels:
   `kubectl get nodes -o wide`
   `kubectl get node <node-name> --show-labels`

Only after these checks pass should the node be treated as ready for workload
placement.

## T1000 First

`enschede-home-t1000-1` is the first meaningful live target because it unlocks
both the new GPU host and additional home capacity.

Validate these points before moving on:

- `hardware.nvidia-container-toolkit` is active
- `nvidia-smi` works on the host
- the node joins as a `k3s` worker
- `/srv/media` is mounted correctly
- the expected `personal-stack/gpu-model-t1000=true` label is present

After that, the first low-risk workload move should happen before widening the
install wave, so the new node is proven with real reconciliation and scheduling.

## Raspberry Pi Wave

Use `enschede-pi-1` as the first ARM rehearsal.

Validate these points:

- the machine boots the `aarch64-linux` host definition cleanly
- the worker joins the cluster
- `Tailscale` connectivity is stable
- the node labels match the inventory intent

Only then repeat the same procedure for `enschede-pi-2` and `enschede-pi-3`.

## Mail Boundary

`stalwart.jorisjonkers.dev` remains the authenticated admin UI route.

Mail protocol traffic should continue to follow the direct DNS model in
[infra/dns/jorisjonkers.dev.zone](/Users/j.w.jonkers/IDEAProjects/personal-stack-2/infra/dns/jorisjonkers.dev.zone:1):

- `mail.jorisjonkers.dev` is the non-proxied A record
- `jorisjonkers.dev` points MX at `mail.jorisjonkers.dev`
- SMTP, IMAP, POP3, and sieve should be published directly, not through Traefik HTTP ingress
