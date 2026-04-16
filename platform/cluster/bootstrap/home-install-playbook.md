# Home Install Playbook

This playbook covers the first clean-install wave for the currently available
home nodes:

- `enschede-t1000-1`
- `enschede-pi-1`
- `enschede-pi-2`
- `enschede-pi-3`

The intended order is:

1. `enschede-t1000-1`
2. `enschede-pi-1`
3. `enschede-pi-2`
4. `enschede-pi-3`

## Expected Sequence

For each node, the sequence is:

1. Create exactly one local deploy key file per host in [platform/nix/authorized-keys/README.md](/Users/j.w.jonkers/IDEAProjects/personal-stack-2/platform/nix/authorized-keys/README.md:1).
   Example: `platform/nix/authorized-keys/enschede-t1000-1.pub`.
2. Keep `ssh` in [fleet.yaml](/Users/j.w.jonkers/IDEAProjects/personal-stack-2/platform/inventory/fleet.yaml:1) pointed at the desired `NixOS` end state, which is `deploy@<node-name>:2222`.
3. Before the first install, add `bootstrap_ssh` for the node with whatever SSH endpoint the current OS already exposes today.
   That can be `root@<node-name>:22`, an existing admin user on another port, or a temporary bootstrap account if you prefer.
4. If the machine is still on `RHEL`, log in through that existing admin path and make sure the `bootstrap_ssh` user has key-based SSH access plus passwordless `sudo`.
   You do not need to move the old OS to port `2222` first.
   If you want a temporary dedicated bootstrap user, this is sufficient:
   `sudo useradd --create-home --groups wheel deploy || sudo usermod --append --groups wheel deploy`
   `sudo install -d -m 700 -o deploy -g deploy /home/deploy/.ssh`
   `sudo install -m 600 -o deploy -g deploy ~/.ssh/id_ed25519.pub /home/deploy/.ssh/authorized_keys`
   `sudo tee /etc/sudoers.d/90-deploy-nopasswd >/dev/null <<'EOF'`
   `deploy ALL=(ALL) NOPASSWD: ALL`
   `EOF`
   `sudo chmod 440 /etc/sudoers.d/90-deploy-nopasswd`
   Verify with:
   `ssh -p <bootstrap-port> <bootstrap-user>@<host> 'sudo -n true'`
5. Run:
   `platform/scripts/install/install-host.sh <node-name>`
   or pass the bootstrap auth explicitly for the current OS session:
   `platform/scripts/install/install-host.sh --ssh-key ~/.ssh/ps-t1000 <node-name>`
   `platform/scripts/install/install-host.sh --ssh-password '<bootstrap-password>' <node-name>`
6. Reboot into the installed `NixOS` system.
7. Validate the base host:
   `hostnamectl`
   `systemctl status tailscaled`
   `systemctl status k3s`
   On freshly installed workers this may still be `failed` until the node has
   joined the tailnet and the worker join token has been copied in the next
   steps.
8. Join the node to the `Tailscale` tailnet:
   `platform/scripts/bootstrap/bootstrap-tailnet.sh <node-name>`
   Then verify:
   `tailscale status`
9. Copy the worker join token from the bootstrap control plane:
   `platform/scripts/bootstrap/bootstrap-k3s-worker.sh <node-name>`
10. Run:
    `platform/scripts/deploy/deploy-host.sh <node-name>`
11. Verify node registration and labels:
    `kubectl get nodes -o wide`
    `kubectl get node <node-name> --show-labels`

Only after these checks pass should the node be treated as ready for workload
placement.

For interactive access after install, prefer a local `~/.ssh/config` entry like:

`Host enschede-t1000-1 enschede-pi-1 enschede-pi-2 enschede-pi-3 enschede-gtx-960m-1 frankfurt-contabo-1`
`  User deploy`
`  Port 2222`
`  IdentityFile ~/.ssh/id_ed25519`
`  IdentitiesOnly yes`

## T1000 First

`enschede-t1000-1` is the first meaningful live target because it unlocks
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
