#!/usr/bin/env bash
# setup.sh — Bootstrap a home network node (Nomad client, Consul client, AdGuard Home).
#
# Idempotent: running twice skips already-completed steps.
#
# Usage:
#   setup.sh <command> [--dry-run]
#
# Commands:
#   install    Install Docker, Consul, Nomad, Tailscale, AdGuard Home, CNI plugins
#   configure  Write configs, create services, set UFW rules, enable services
#   full       Run install + configure sequentially
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STACK_DIR="${STACK_DIR:-/opt/personal-stack}"
HOME_NODE_DIR="${ROOT_DIR}/infra/home-node"
ENV_FILE="${ENV_FILE:-${STACK_DIR}/.home-node.env}"
MODE="apply"

ADGUARD_VERSION="${ADGUARD_VERSION:-v0.107.55}"

# ── Usage ─────────────────────────────────────────────────────────────────

usage() {
  cat <<'EOF'
Usage: setup.sh <command> [--dry-run]

Commands:
  install    Install Docker, Consul, Nomad, Tailscale, AdGuard Home, CNI plugins
  configure  Write configs, create services, set UFW rules, enable services
  full       Run install + configure sequentially
EOF
}

# ── Utility functions ─────────────────────────────────────────────────────

run() {
  echo "+ $*"
  [[ "${MODE}" == "apply" ]] && "$@"
}

require_root() {
  [[ "$(id -u)" -eq 0 ]] || { echo "Run as root." >&2; exit 1; }
}

require_env() {
  local var="$1"
  [[ -n "${!var:-}" ]] || { echo "Required env var ${var} is not set." >&2; exit 1; }
}

load_env() {
  if [[ -f "${ENV_FILE}" ]]; then
    set -a; source "${ENV_FILE}"; set +a
  fi
}

shell_single_quote() {
  printf "'%s'" "${1//\'/\'\"\'\"\'}"
}

# ── install command ───────────────────────────────────────────────────────

install_command() {
  require_root
  load_env

  run apt-get update
  run apt-get install -y gpg curl unzip ca-certificates lsb-release jq dnsutils

  # Docker
  if ! command -v docker >/dev/null 2>&1; then
    run curl -fsSL https://get.docker.com -o /tmp/get-docker.sh
    run sh /tmp/get-docker.sh
    run rm -f /tmp/get-docker.sh
  fi
  run systemctl enable docker
  run systemctl start docker

  # HashiCorp repository
  if [[ ! -f /usr/share/keyrings/hashicorp-archive-keyring.gpg ]]; then
    run curl -fsSL https://apt.releases.hashicorp.com/gpg -o /tmp/hashicorp.gpg
    run gpg --dearmor -o /usr/share/keyrings/hashicorp-archive-keyring.gpg /tmp/hashicorp.gpg
    run rm -f /tmp/hashicorp.gpg
  fi
  if [[ ! -f /etc/apt/sources.list.d/hashicorp.list ]]; then
    if [[ "${MODE}" == "apply" ]]; then
      echo "deb [signed-by=/usr/share/keyrings/hashicorp-archive-keyring.gpg] https://apt.releases.hashicorp.com $(lsb_release -cs) main" \
        | tee /etc/apt/sources.list.d/hashicorp.list >/dev/null
    else
      echo "+ write /etc/apt/sources.list.d/hashicorp.list"
    fi
  fi
  run apt-get update
  run apt-get install -y consul nomad

  # CNI plugins (required for Nomad bridge networking)
  if [[ ! -f /opt/cni/bin/bridge ]]; then
    CNI_VERSION="v1.4.0"
    run mkdir -p /opt/cni/bin
    run curl -sL "https://github.com/containernetworking/plugins/releases/download/${CNI_VERSION}/cni-plugins-linux-amd64-${CNI_VERSION}.tgz" \
      -o /tmp/cni-plugins.tgz
    run tar -xz -C /opt/cni/bin -f /tmp/cni-plugins.tgz
    run rm -f /tmp/cni-plugins.tgz
  fi

  # Tailscale
  if ! command -v tailscale >/dev/null 2>&1; then
    run curl -fsSL https://tailscale.com/install.sh -o /tmp/install-tailscale.sh
    run sh /tmp/install-tailscale.sh
    run rm -f /tmp/install-tailscale.sh
  fi

  # AdGuard Home
  if [[ ! -f /opt/adguard-home/AdGuardHome ]]; then
    local arch="amd64"
    run mkdir -p /opt/adguard-home
    run curl -sL "https://github.com/AdguardTeam/AdGuardHome/releases/download/${ADGUARD_VERSION}/AdGuardHome_linux_${arch}.tar.gz" \
      -o /tmp/adguard-home.tar.gz
    run tar -xz -C /tmp -f /tmp/adguard-home.tar.gz
    run cp /tmp/AdGuardHome/AdGuardHome /opt/adguard-home/AdGuardHome
    run chmod +x /opt/adguard-home/AdGuardHome
    run rm -rf /tmp/AdGuardHome /tmp/adguard-home.tar.gz
  fi

  # Samba
  if ! command -v smbd >/dev/null 2>&1; then
    run apt-get install -y samba
  fi

  # Data directories
  run mkdir -p /etc/consul.d /etc/nomad.d
  run mkdir -p /opt/consul /opt/nomad
  run mkdir -p /srv/nomad/lightrag /srv/nomad/alloy
  run mkdir -p /srv/nomad/qbittorrent /srv/nomad/prowlarr /srv/nomad/sonarr /srv/nomad/radarr /srv/nomad/jellyfin
  run mkdir -p /mnt/media

  run chown -R consul:consul /opt/consul
  run chown -R nomad:nomad /opt/nomad

  # Volume ownership matching container UIDs (linuxserver.io convention: UID 1000)
  run chown -R 1000:1000 /srv/nomad/lightrag
  run chown -R 1000:1000 /srv/nomad/qbittorrent /srv/nomad/prowlarr /srv/nomad/sonarr /srv/nomad/radarr /srv/nomad/jellyfin

  # Ensure tun module is available for gluetun VPN
  run modprobe tun || true
  if ! grep -q '^tun$' /etc/modules 2>/dev/null; then
    echo "tun" >> /etc/modules
  fi

  # GHCR login
  if [[ -n "${GHCR_USER:-}" && -n "${GHCR_TOKEN:-}" ]]; then
    echo "+ docker login ghcr.io"
    if [[ "${MODE}" == "apply" ]]; then
      printf '%s' "${GHCR_TOKEN}" | docker login ghcr.io -u "${GHCR_USER}" --password-stdin >/dev/null
    fi
  fi

  echo "Package installation complete."
}

# ── configure command ─────────────────────────────────────────────────────

configure_command() {
  require_root
  load_env

  require_env VPS_TAILSCALE_IP
  require_env CONSUL_ENCRYPT_KEY
  require_env TAILSCALE_AUTH_KEY

  # Tailscale: bring up VPN mesh
  if ! tailscale status >/dev/null 2>&1; then
    local ts_args=(--authkey="${TAILSCALE_AUTH_KEY}" --hostname=personal-stack-home)
    [[ -n "${HEADSCALE_URL:-}" ]] && ts_args+=(--login-server="${HEADSCALE_URL}")
    run tailscale up "${ts_args[@]}"
  fi

  local tailscale_ip
  tailscale_ip="$(tailscale ip -4)"
  [[ -n "${tailscale_ip}" ]] || { echo "Could not obtain Tailscale IP." >&2; exit 1; }
  echo "Tailscale IP: ${tailscale_ip}"

  # Install and template Consul client config
  install -m 0644 "${HOME_NODE_DIR}/configs/consul-client.hcl" /etc/consul.d/consul.hcl
  sed -i "s/__TAILSCALE_IP__/${tailscale_ip}/g"           /etc/consul.d/consul.hcl
  sed -i "s/__VPS_TAILSCALE_IP__/${VPS_TAILSCALE_IP}/g"   /etc/consul.d/consul.hcl
  sed -i "s/__CONSUL_ENCRYPT_KEY__/${CONSUL_ENCRYPT_KEY}/g" /etc/consul.d/consul.hcl
  echo "Consul client config written"

  # Install and template Nomad client config
  install -m 0644 "${HOME_NODE_DIR}/configs/nomad-client.hcl" /etc/nomad.d/nomad.hcl
  sed -i "s/__TAILSCALE_IP__/${tailscale_ip}/g"           /etc/nomad.d/nomad.hcl
  sed -i "s/__VPS_TAILSCALE_IP__/${VPS_TAILSCALE_IP}/g"   /etc/nomad.d/nomad.hcl
  echo "Nomad client config written"

  # AdGuard Home config
  install -m 0644 "${HOME_NODE_DIR}/adguard/AdGuardHome.yaml" /opt/adguard-home/AdGuardHome.yaml
  if [[ -n "${HOME_LAN_IP:-}" ]]; then
    sed -i "s/__HOME_LAN_IP__/${HOME_LAN_IP}/g" /opt/adguard-home/AdGuardHome.yaml
    echo "AdGuard DNS rewrites configured for LAN IP ${HOME_LAN_IP}"
  fi

  # Media HDD mount unit
  if [[ -n "${MEDIA_DISK_UUID:-}" ]]; then
    install -m 0644 "${HOME_NODE_DIR}/systemd/mnt-media.mount" /etc/systemd/system/mnt-media.mount
    sed -i "s/__MEDIA_DISK_UUID__/${MEDIA_DISK_UUID}/g" /etc/systemd/system/mnt-media.mount
    sed -i "s/__MEDIA_DISK_FS__/${MEDIA_DISK_FS:-ext4}/g" /etc/systemd/system/mnt-media.mount
    echo "Media HDD mount unit configured (UUID: ${MEDIA_DISK_UUID})"
  fi

  # Samba config
  install -m 0644 "${HOME_NODE_DIR}/samba/smb.conf" /etc/samba/smb.conf
  if [[ -n "${SAMBA_PASSWORD:-}" ]]; then
    id media >/dev/null 2>&1 || useradd -r -s /usr/sbin/nologin media
    (echo "${SAMBA_PASSWORD}"; echo "${SAMBA_PASSWORD}") | smbpasswd -a media -s 2>/dev/null || true
    echo "Samba user 'media' configured"
  fi

  # Systemd units
  install -m 0644 "${HOME_NODE_DIR}/systemd/adguard-home.service" /etc/systemd/system/adguard-home.service
  install -m 0644 "${HOME_NODE_DIR}/systemd/home-node-update.service" /etc/systemd/system/home-node-update.service
  install -m 0644 "${HOME_NODE_DIR}/systemd/home-node-update.timer" /etc/systemd/system/home-node-update.timer

  # Nomad advertise script: picks Tailscale IP if up, else primary outbound IP
  install -m 0755 "${ROOT_DIR}/infra/scripts/nomad-advertise.sh" /usr/local/bin/nomad-advertise.sh

  # Systemd ordering: Nomad starts after Docker, Consul, and Tailscale.
  # ExecStartPre generates /etc/nomad.d/advertise.hcl dynamically.
  mkdir -p /etc/systemd/system/nomad.service.d
  cat <<'EOF' > /etc/systemd/system/nomad.service.d/override.conf
[Unit]
After=network-online.target docker.service consul.service tailscaled.service
Wants=network-online.target docker.service consul.service tailscaled.service

[Service]
ExecStartPre=/usr/local/bin/nomad-advertise.sh
EOF

  # Firewall
  if command -v ufw >/dev/null 2>&1; then
    run ufw default deny incoming
    run ufw default allow outgoing
    run ufw allow 22/tcp                                              comment 'ssh'
    run ufw allow 53/tcp                                              comment 'dns'
    run ufw allow 53/udp                                              comment 'dns'
    run ufw allow in on tailscale0                                    comment 'tailscale mesh traffic'
    run ufw allow proto tcp from 192.168.0.0/16 to any port 445      comment 'samba'
    run ufw allow proto tcp from 192.168.0.0/16 to any port 8096     comment 'jellyfin'
    run ufw --force enable
  fi

  run systemctl daemon-reload
  run systemctl enable consul nomad adguard-home home-node-update.timer smbd

  # Mount media HDD if configured
  if [[ -n "${MEDIA_DISK_UUID:-}" ]]; then
    run systemctl enable mnt-media.mount
    run systemctl start mnt-media.mount || true
    # Ensure key media directories exist (preserves existing structure)
    run mkdir -p /mnt/media/Completed /mnt/media/Downloading /mnt/media/Films /mnt/media/Series /mnt/media/Anime /mnt/media/TimeMachine
    run chown -R 1000:1000 /mnt/media/Completed /mnt/media/Downloading /mnt/media/Films /mnt/media/Series /mnt/media/Anime
  fi

  # Start services
  run systemctl restart consul
  echo "Waiting for Consul to join cluster..."
  for i in $(seq 1 30); do
    if consul members 2>/dev/null | grep -q "alive"; then
      echo "Consul cluster joined"
      break
    fi
    sleep 2
  done

  run systemctl restart nomad
  echo "Waiting for Nomad client to register..."
  for i in $(seq 1 30); do
    if nomad node status 2>/dev/null | grep -q "ready"; then
      echo "Nomad client ready"
      break
    fi
    sleep 2
  done

  run systemctl restart adguard-home
  run systemctl start home-node-update.timer

  echo "Home node configuration complete."
  echo ""
  echo "Manual step: Set this machine's LAN IP as primary DNS in your router's DHCP settings."
}

# ── full command ──────────────────────────────────────────────────────────

full_command() {
  install_command
  configure_command
}

# ── main ──────────────────────────────────────────────────────────────────

main() {
  if [[ $# -lt 1 ]]; then
    usage; exit 1
  fi

  local cmd="$1"; shift

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --dry-run) MODE="dry-run" ;;
      *) echo "Unknown flag: $1" >&2; exit 1 ;;
    esac
    shift
  done

  case "${cmd}" in
    install)   install_command ;;
    configure) configure_command ;;
    full)      full_command ;;
    *)         echo "Unknown command: ${cmd}" >&2; usage; exit 1 ;;
  esac
}

main "$@"
