# Tailscale Tailnet Bootstrap

This playbook makes `Tailscale` the primary private network for node-to-node
operations during the platform migration.

Use the hosted `Tailscale` control plane and admin console instead of
self-hosting `Headscale`.

## Why This Is The Bootstrap Path

- the admin console already gives you the UI for machine inventory, auth keys,
  route approval, and policy editing
- the control plane stays available even if one of your own nodes is down
- each node can run the official `tailscaled` client directly, so there is no
  extra VPN coordination service to migrate first

## Recommended Model

1. Use one-off auth keys from the `Tailscale` admin console for first joins.
2. Tag non-human machines in the admin console policy, not by user identity.
3. Keep public ingress on public DNS and public IPs.
4. Use the tailnet for:
   `SSH`, `k3s` inter-node traffic, backup traffic, and private admin access.
5. Add subnet routers only for devices that cannot run the `Tailscale` client.

## First Join Flow Per Host

1. Install the host into `NixOS`.
2. In the `Tailscale admin console`, create a one-off auth key for the server.
   Prefer a tagged machine identity such as:
   `tag:k3s-worker`, `tag:utility-host`, `tag:site-enschede`.
3. On your workstation, export the auth key without putting it in shell history:
   `export TS_AUTH_KEY=$(cat)`
   Paste the key, then press `Ctrl+D`.
4. Run:
   `platform/scripts/bootstrap/bootstrap-tailnet.sh <node-name>`
5. Verify:
   `ssh -p 2222 deploy@<node-name> tailscale status`
   `ssh -p 2222 deploy@<node-name> tailscale ip -4`
6. Revoke the one-off auth key in the admin console if it still appears as live.

## Cluster Follow-Up

Once `frankfurt-contabo-1` is on the tailnet and stable, switch
[fleet.yaml](/Users/j.w.jonkers/IDEAProjects/personal-stack-2/platform/inventory/fleet.yaml:1)
`cluster.kubernetes.api_server_endpoint` from the public IP to the Frankfurt
control-plane `Tailscale` or `MagicDNS` address.

That change should happen before widening the worker install wave so new nodes
join `k3s` over the private overlay instead of the public path.
