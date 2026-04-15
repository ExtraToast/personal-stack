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

1. Create `platform/nix/authorized-keys.nix` from [authorized-keys.nix.example](/Users/j.w.jonkers/IDEAProjects/personal-stack-2/platform/nix/authorized-keys.nix.example:1).
2. Make the target reachable at the `ssh.host` recorded in [fleet.yaml](/Users/j.w.jonkers/IDEAProjects/personal-stack-2/platform/inventory/fleet.yaml:1).
   The bootstrap default is `deploy@<node-name>:2222`; swap `ssh.host` to a raw LAN IP if local name resolution is not ready yet.
3. If the machine is still on `RHEL`, prepare a key-only `deploy` bootstrap account first:
   `sudo useradd --create-home --groups wheel deploy || sudo usermod --append --groups wheel deploy`
   `sudo install -d -m 700 -o deploy -g deploy /home/deploy/.ssh`
   `sudo install -m 600 -o deploy -g deploy ~/.ssh/id_ed25519.pub /home/deploy/.ssh/authorized_keys`
   `sudo tee /etc/sudoers.d/90-deploy-nopasswd >/dev/null <<'EOF'`
   `deploy ALL=(ALL) NOPASSWD: ALL`
   `EOF`
   `sudo chmod 440 /etc/sudoers.d/90-deploy-nopasswd`
   `sudo tee /etc/ssh/sshd_config.d/10-personal-stack-bootstrap.conf >/dev/null <<'EOF'`
   `Port 2222`
   `AllowUsers deploy`
   `PubkeyAuthentication yes`
   `PasswordAuthentication no`
   `KbdInteractiveAuthentication no`
   `PermitRootLogin no`
   `EOF`
   `sudo dnf install -y policycoreutils-python-utils`
   `sudo semanage port -a -t ssh_port_t -p tcp 2222 || sudo semanage port -m -t ssh_port_t -p tcp 2222`
   `sudo firewall-cmd --permanent --add-port=2222/tcp`
   `sudo firewall-cmd --reload`
   `sudo systemctl restart sshd`
   Verify with:
   `ssh -p 2222 deploy@<host> 'sudo -n true'`
   Only after that should you close port `22` on the old OS if it is still open.
4. Run:
   `platform/scripts/install/install-host.sh <node-name>`
5. Reboot into the installed `NixOS` system.
6. Validate the base host:
   `hostnamectl`
   `tailscale status`
   `systemctl status k3s`
7. Run:
   `platform/scripts/deploy/deploy-host.sh <node-name>`
8. Verify node registration and labels:
   `kubectl get nodes -o wide`
   `kubectl get node <node-name> --show-labels`

Only after these checks pass should the node be treated as ready for workload
placement.

For interactive access after install, prefer a local `~/.ssh/config` entry like:

`Host enschede-home-t1000-1 enschede-pi-1 enschede-pi-2 enschede-pi-3 enschede-home-gtx960m-1 frankfurt-contabo-1`
`  User deploy`
`  Port 2222`
`  IdentityFile ~/.ssh/id_ed25519`
`  IdentitiesOnly yes`

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
