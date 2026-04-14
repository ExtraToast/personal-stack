# Home Node Setup

Bootstrap a home network machine as a Nomad/Consul client node that joins the
VPS cluster over a Tailscale (Headscale) mesh VPN. Runs AdGuard Home for
network-wide DNS, a media stack (Jellyfin, Jellyseerr, Bazarr, Sonarr,
Radarr, Prowlarr, qBittorrent) with VPN-routed downloads, and Samba shares
including Time Machine.

## Architecture

```
Internet -> Cloudflare -> VPS Traefik -> Tailscale -> Home Node
                                                                       |
LAN devices -> AdGuard DNS rewrite -> Home Node directly (no auth)     |
                                                                       |
              +------------ PIA WireGuard (gluetun) ----------------+  |
              |  qBittorrent (8080)  |  Prowlarr (9696)             |  |
              |  Nomad bridge mode - shared network namespace       |  |
              +-----------------------------------------------------+  |
                                    |                                  |
              +---------------------+---- host networking ----------+  |
              |  Bazarr (6767)  Sonarr (8989)  Radarr (7878)       |  |
              |  Jellyfin (8096)  Jellyseerr (5055)                |  |
              +---------------------+------------------------------+   |
                                    |                                  |
                          /mnt/media (6TB HDD)                         |
                          +-- Completed/     completed downloads       |
                          +-- Downloading/   in-progress downloads     |
                          +-- Films/         Samba [databeast]         |
                          +-- Series/        share (read-only          |
                          +-- Anime/         for guests)               |
                          +-- TimeMachine/   Samba [timemachine] 300GB |
```

## Part 1: VPS Preparation

These steps are done once on the VPS before setting up the home node.

### 1.1 Generate Consul gossip key

```bash
consul keygen
# Save the output - both nodes need the same key
```

### 1.2 Deploy Headscale and create auth keys

After merging this branch and the CI deploys to VPS:

```bash
ssh -p 2222 your-vps

# Verify Headscale is running
curl -I https://headscale.jorisjonkers.dev/key

# Create user and auth keys
ALLOC=$(nomad job allocs -json headscale | jq -r '.[0].ID')
nomad alloc exec "$ALLOC" headscale users create personal-stack
nomad alloc exec "$ALLOC" headscale preauthkeys create \
  --user personal-stack --reusable --expiration 720h
# Save the key - this is TAILSCALE_AUTH_KEY for both nodes
```

### 1.3 Register VPS with Headscale

```bash
# Add to /opt/personal-stack/.nomad-bootstrap.env:
CONSUL_ENCRYPT_KEY=<key from 1.1>
TAILSCALE_AUTH_KEY=<key from 1.2>
HEADSCALE_URL=https://headscale.jorisjonkers.dev

# Re-run configure (pass env vars through sudo)
sudo tailscale logout  # if already connected to Tailscale SaaS
source /opt/personal-stack/.nomad-bootstrap.env && \
  sudo CONSUL_ENCRYPT_KEY="$CONSUL_ENCRYPT_KEY" \
       TAILSCALE_AUTH_KEY="$TAILSCALE_AUTH_KEY" \
       HEADSCALE_URL="$HEADSCALE_URL" \
  bash /opt/personal-stack/infra/scripts/setup.sh configure

# Note the Tailscale IP - the home node needs it
tailscale ip -4
```

### 1.4 Seed PIA VPN credentials in Vault

```bash
source /opt/personal-stack/.nomad-bootstrap.env && \
  VAULT_ADDR=http://127.0.0.1:8200 VAULT_TOKEN="$VAULT_ROOT_TOKEN" \
  vault kv put secret/platform/media \
    "pia.username=$PIA_USERNAME" \
    "pia.password=$PIA_PASSWORD" \
    "pia.server_regions=Netherlands"
# Optional: if you have a WireGuard private key, add:
#   "pia.wireguard_private_key=<KEY>"
# Gluetun auto-negotiates with PIA when omitted.

# Register the new Vault policy and role
source /opt/personal-stack/.nomad-bootstrap.env && \
  VAULT_ADDR=http://127.0.0.1:8200 VAULT_TOKEN="$VAULT_ROOT_TOKEN" \
  bash /opt/personal-stack/infra/scripts/setup.sh prepare-vault
```

## Part 2: Home Node Setup

### 2.1 Install Ubuntu

Install Ubuntu 22.04 or 24.04 on the laptop. Connect the 6TB HDD.

### 2.2 Find HDD UUID

```bash
sudo blkid
# Find your 6TB drive, note the UUID and TYPE (e.g. ext4)
# Example: /dev/sdb1: UUID="abc123-..." TYPE="ext4"
```

### 2.3 Clone repo and create env file

```bash
sudo git clone https://github.com/ExtraToast/personal-stack.git /opt/personal-stack
cd /opt/personal-stack

sudo tee /opt/personal-stack/.home-node.env > /dev/null <<'EOF'
VPS_TAILSCALE_IP=<from VPS step 1.3>
CONSUL_ENCRYPT_KEY=<from step 1.1>
TAILSCALE_AUTH_KEY=<from step 1.2>
HEADSCALE_URL=https://headscale.jorisjonkers.dev
GHCR_USER=ExtraToast
GHCR_TOKEN=<your GitHub PAT with read:packages>
MEDIA_DISK_UUID=<from step 2.2>
MEDIA_DISK_FS=ext4
SAMBA_PASSWORD=<choose a password>
HOME_LAN_IP=<this machine's LAN IP, e.g. 192.168.1.100>
EOF
sudo chmod 600 /opt/personal-stack/.home-node.env
```

### 2.4 Run the bootstrap

```bash
sudo bash /opt/personal-stack/infra/home-node/setup.sh full
```

This installs Docker, Consul, Nomad, Tailscale, AdGuard Home, Samba, mounts the
HDD, creates media directories, configures the firewall, and starts all services.

### 2.5 Deploy media jobs

```bash
export NOMAD_ADDR=http://127.0.0.1:4646
export NOMAD_TOKEN=<your Nomad token>

nomad job run infra/nomad/jobs/media/downloads.nomad.hcl
nomad job run infra/nomad/jobs/media/bazarr.nomad.hcl
nomad job run infra/nomad/jobs/media/sonarr.nomad.hcl
nomad job run infra/nomad/jobs/media/radarr.nomad.hcl
nomad job run infra/nomad/jobs/media/jellyfin.nomad.hcl
nomad job run infra/nomad/jobs/media/jellyseerr.nomad.hcl
```

### 2.6 Configure router DNS

Set this machine's LAN IP as the primary DNS server in your router's DHCP
settings, with `1.1.1.1` as fallback.

## Part 3: App Configuration

### 3.1 qBittorrent

Open `http://<home-ip>:8080` (default login: admin / adminadmin).

- Settings > Downloads > Default Save Path: `/media/Completed`
- Settings > Downloads > Keep incomplete in: `/media/Downloading`
- Settings > WebUI > change the default password

### 3.2 Prowlarr

Open `http://<home-ip>:9696`.

- Add your indexers
- Settings > Apps > Add Sonarr (host: `<home-ip>`, port: `8989`)
- Settings > Apps > Add Radarr (host: `<home-ip>`, port: `7878`)

### 3.3 Sonarr

Open `http://<home-ip>:8989`.

- Settings > Media Management > Root Folders > Add `/media/Series`
- Settings > Media Management > Root Folders > Add `/media/Anime` (if managing anime separately)
- Settings > Download Clients > Add qBittorrent (host: `<home-ip>`, port: `8080`)

### 3.4 Radarr

Open `http://<home-ip>:7878`.

- Settings > Media Management > Root Folders > Add `/media/Films`
- Settings > Download Clients > Add qBittorrent (host: `<home-ip>`, port: `8080`)

### 3.5 Bazarr

Open `http://<home-ip>:6767`.

- Complete the setup wizard
- Add Sonarr using `http://127.0.0.1:8989`
- Add Radarr using `http://127.0.0.1:7878`
- Keep media paths aligned with the shared `/media` mount

### 3.6 Jellyfin

Open `http://<home-ip>:8096`.

- Complete the setup wizard
- Add library: Movies > `/media/Films`
- Add library: TV Shows > `/media/Series`
- Add library: Anime > `/media/Anime`
- Add library: Music > `/media/Music` (optional)

### 3.7 Jellyseerr

Open `http://<home-ip>:5055`.

- Complete the setup wizard
- Connect Jellyfin using `http://<home-ip>:8096` and your Jellyfin admin account
- Add Sonarr using `http://<home-ip>:8989`
- Add Radarr using `http://<home-ip>:7878`

### 3.8 Time Machine

On your Mac:

```bash
# Or use System Settings > General > Time Machine > Add Backup Destination
tmutil setdestination smb://media@<home-ip>/timemachine
# Share name is "timemachine", data lives at /mnt/media/TimeMachine
```

## Migrating from Existing Installations

### Export Sonarr config

```bash
# On the machine running Sonarr, copy the config directory:
# Default locations:
#   Linux: ~/.config/Sonarr/
#   Docker: the /config volume mount
cp -r /path/to/sonarr/config/Backups/scheduled/*.zip .

# Restore on new instance:
# 1. Open Sonarr at http://<home-ip>:8989
# 2. System > Backup > Restore Backup > upload the .zip
```

### Export Radarr config

```bash
# Same pattern as Sonarr:
cp -r /path/to/radarr/config/Backups/scheduled/*.zip .

# Restore: Radarr > System > Backup > Restore Backup
```

### Export Prowlarr config (from Jackett)

If migrating from Jackett to Prowlarr:

```bash
# Jackett doesn't export directly to Prowlarr.
# Instead, in Prowlarr:
# 1. Indexers > Add Indexer > search for each indexer you had in Jackett
# 2. Re-enter your credentials for each tracker
#
# Alternatively, use Prowlarr's Jackett migration:
# 1. Ensure Jackett is still running
# 2. In Prowlarr: Indexers > Add > "Jackett" (under Other)
# 3. Enter Jackett URL and API key
# 4. This proxies through Jackett while you migrate indexers one by one
```

### Export existing Sonarr/Radarr databases

For a full migration preserving history, series, and settings:

```bash
# Stop the old instance first, then copy the SQLite database:
cp /old-sonarr/config/sonarr.db /srv/nomad/sonarr/sonarr.db
cp /old-radarr/config/radarr.db /srv/nomad/radarr/radarr.db

# Fix ownership:
sudo chown 1000:1000 /srv/nomad/sonarr/sonarr.db /srv/nomad/radarr/radarr.db

# Start the new instances - they'll pick up the existing database.
# You'll need to update paths (Root Folders, Download Client) in the UI
# since container mount paths may differ from the old setup.
```

## Access Patterns

| Service      | LAN URL                                    | External URL                           |
| ------------ | ------------------------------------------ | -------------------------------------- |
| Jellyfin     | `http://jellyfin.jorisjonkers.dev:8096`    | `https://jellyfin.jorisjonkers.dev`    |
| Jellyseerr   | `http://jellyseerr.jorisjonkers.dev:5055`  | `https://jellyseerr.jorisjonkers.dev`  |
| Bazarr       | `http://bazarr.jorisjonkers.dev:6767`      | `https://bazarr.jorisjonkers.dev`      |
| Sonarr       | `http://sonarr.jorisjonkers.dev:8989`      | `https://sonarr.jorisjonkers.dev`      |
| Radarr       | `http://radarr.jorisjonkers.dev:7878`      | `https://radarr.jorisjonkers.dev`      |
| Prowlarr     | `http://prowlarr.jorisjonkers.dev:9696`    | `https://prowlarr.jorisjonkers.dev`    |
| qBittorrent  | `http://qbittorrent.jorisjonkers.dev:8080` | `https://qbittorrent.jorisjonkers.dev` |
| Samba        | `smb://<home-ip>/databeast`                | not exposed                            |
| Time Machine | `smb://media@<home-ip>/timemachine`        | not exposed                            |
| AdGuard      | `http://127.0.0.1:3000` (SSH tunnel)       | not exposed                            |

LAN URLs work because AdGuard DNS rewrites resolve these subdomains to the home
node's LAN IP. External URLs go through VPS Traefik. `Jellyfin` and
`Jellyseerr` use their own auth; the admin tools stay behind Traefik
forward-auth. Note: LAN URLs use HTTP with the service port since there's no
Traefik on the home node.

## Verification

```bash
# Cluster
consul members                              # 2 nodes
nomad node status                           # home node "ready"

# Media stack
nomad job status downloads                  # gluetun + qbittorrent + prowlarr
nomad job status bazarr
nomad job status sonarr
nomad job status radarr
nomad job status jellyfin
nomad job status jellyseerr

# VPN check - should show PIA IP, not your home IP
ALLOC=$(nomad job allocs -json downloads | jq -r '.[0].ID')
nomad alloc exec -task qbittorrent "$ALLOC" curl -s ifconfig.me

# DNS rewrites (from a LAN device using AdGuard DNS)
dig @<home-ip> jellyfin.jorisjonkers.dev    # -> home LAN IP
dig @<home-ip> jellyseerr.jorisjonkers.dev  # -> home LAN IP
dig @<home-ip> bazarr.jorisjonkers.dev      # -> home LAN IP

# Samba
smbclient -L //<home-ip>/ -N               # lists media + timemachine

# HDD
mount | grep /mnt/media                     # mounted
df -h /mnt/media                            # shows 6TB
```

## Layout

```
infra/home-node/
  setup.sh                    Main bootstrap script
  update.sh                   Auto-update (git pull + re-configure)
  README.md                   This file
  configs/
    consul-client.hcl         Consul client config (templated)
    nomad-client.hcl          Nomad client config (templated)
  adguard/
    AdGuardHome.yaml          DNS config with ad blocking + LAN rewrites
  samba/
    smb.conf                  Samba config (media + Time Machine shares)
  systemd/
    adguard-home.service      AdGuard Home systemd unit
    mnt-media.mount           6TB HDD auto-mount
    home-node-update.service  Auto-update oneshot
    home-node-update.timer    Daily timer (04:00)

infra/nomad/jobs/media/
  downloads.nomad.hcl         gluetun VPN + qBittorrent + Prowlarr
  bazarr.nomad.hcl            Subtitle management
  sonarr.nomad.hcl            TV show management
  radarr.nomad.hcl            Movie management
  jellyfin.nomad.hcl          Media server
  jellyseerr.nomad.hcl        Request management
```
