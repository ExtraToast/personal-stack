# Home Node Setup

Bootstrap a home network machine as a Nomad/Consul client node that joins the
VPS cluster over a Tailscale (Headscale) mesh VPN. Also runs AdGuard Home as a
network-wide DNS server with ad/tracker blocking.

## Architecture

```
                     Tailscale Mesh (WireGuard)
VPS (Contabo)  <================================>  Home Node (LAN)
  Nomad server + client                              Nomad client only
  Consul server + client                              Consul client only
  Vault, Traefik, DBs, apps                           Alloy (observability)
  Headscale control server                            AdGuard Home (DNS)
```

## Prerequisites

- Fresh Ubuntu 22.04 or 24.04 install
- VPS already running with Headscale deployed
- Consul gossip encryption key (from `consul keygen`)
- Tailscale pre-auth key (from Headscale)
- GitHub PAT with `read:packages` scope

## Environment File

Create `/opt/personal-stack/.home-node.env` with `chmod 600`:

```
VPS_TAILSCALE_IP=100.x.y.z
CONSUL_ENCRYPT_KEY=<base64 from consul keygen>
TAILSCALE_AUTH_KEY=<from headscale preauthkeys create>
HEADSCALE_URL=https://headscale.jorisjonkers.dev
GHCR_USER=ExtraToast
GHCR_TOKEN=ghp_...
```

## Quick Start

```bash
sudo git clone https://github.com/ExtraToast/personal-stack.git /opt/personal-stack
# Create .home-node.env as above
sudo bash /opt/personal-stack/infra/home-node/setup.sh full
```

## Commands

| Command                    | Description                                                         |
| -------------------------- | ------------------------------------------------------------------- |
| `setup.sh install`         | Install Docker, Consul, Nomad, Tailscale, AdGuard Home, CNI plugins |
| `setup.sh configure`       | Template configs, start services, configure firewall                |
| `setup.sh full`            | Run install + configure                                             |
| `setup.sh <cmd> --dry-run` | Print commands without executing                                    |

## What Gets Installed

| Component    | Role                                                                   |
| ------------ | ---------------------------------------------------------------------- |
| Docker       | Container runtime for Nomad jobs                                       |
| Consul       | Client-only, joins VPS server over Tailscale                           |
| Nomad        | Client-only, registers with `node_type=home` and `gpu=gtx960m` meta    |
| Tailscale    | WireGuard mesh VPN, connects to self-hosted Headscale                  |
| AdGuard Home | DNS server on port 53 with ad blocking and `.consul` forwarding        |
| Alloy        | Deployed automatically by Nomad (system job), ships logs/traces to VPS |

## Automatic Updates

A systemd timer runs `/opt/personal-stack/infra/home-node/update.sh` daily at
04:00. It pulls the latest config from git and re-runs `setup.sh configure`.

To update manually:

```bash
sudo bash /opt/personal-stack/infra/home-node/update.sh
```

## Firewall Rules

| Port | Protocol | Interface  | Purpose                                |
| ---- | -------- | ---------- | -------------------------------------- |
| 22   | TCP      | any        | SSH                                    |
| 53   | TCP+UDP  | any        | DNS (AdGuard Home)                     |
| all  | all      | tailscale0 | Cluster traffic (Consul, Nomad, Vault) |

All other incoming traffic is denied by default.

## AdGuard Home

- DNS: `0.0.0.0:53` (serves entire LAN)
- Web UI: `127.0.0.1:3000` (localhost only; use SSH tunnel for remote access)
- Upstream: Cloudflare DNS-over-HTTPS
- Consul: `.consul` queries forwarded to local Consul agent on port 8600
- Filters: AdGuard DNS filter, AdAway, EasyList, EasyPrivacy, malware domains

Set this machine's LAN IP as the primary DNS server in your router's DHCP
settings, with `1.1.1.1` as fallback.

## Verification

```bash
consul members                              # 2 nodes
nomad node status                           # home node shows "ready"
dig @localhost google.com                   # DNS resolves
dig @localhost ads.google.com               # blocked (0.0.0.0)
dig @localhost consul.service.consul        # Consul DNS forwarding
```

## Layout

```
infra/home-node/
  setup.sh                    Main bootstrap script
  update.sh                   Auto-update (git pull + re-configure)
  configs/
    consul-client.hcl         Consul client config (templated)
    nomad-client.hcl          Nomad client config (templated)
  adguard/
    AdGuardHome.yaml           Pre-seeded AdGuard Home config
  systemd/
    adguard-home.service       AdGuard Home systemd unit
    home-node-update.service   Auto-update oneshot
    home-node-update.timer     Daily timer (04:00)
```
