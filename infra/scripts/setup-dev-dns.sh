#!/usr/bin/env bash
# Sets up wildcard DNS so *.jorisjonkers.test resolves to 127.0.0.1.
# Idempotent — safe to run multiple times.
#
# macOS: uses /etc/resolver/ (built-in wildcard DNS support)
# Linux: installs and configures dnsmasq, with a CI-safe /etc/hosts fallback

set -euo pipefail

DOMAIN="jorisjonkers.test"
IP="127.0.0.1"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HOSTS=(
  "$DOMAIN"
  "auth.$DOMAIN"
  "assistant.$DOMAIN"
  "vault.$DOMAIN"
  "rabbitmq.$DOMAIN"
  "mail.$DOMAIN"
  "n8n.$DOMAIN"
  "grafana.$DOMAIN"
  "status.$DOMAIN"
  "traefik.$DOMAIN"
  "stalwart.$DOMAIN"
)

setup_hosts_entries() {
  local marker_begin="# personal-stack dev domains begin"
  local marker_end="# personal-stack dev domains end"
  local hosts_line="$IP ${HOSTS[*]}"

  if sudo grep -qF "$marker_begin" /etc/hosts 2>/dev/null; then
    echo "DNS: dev host entries already configured."
    return
  fi

  echo "DNS: Configuring explicit host entries..."
  {
    echo ""
    echo "$marker_begin"
    echo "$hosts_line"
    echo "$marker_end"
  } | sudo tee -a /etc/hosts > /dev/null
  echo "DNS: Done."
}

setup_dns_macos() {
  local resolver_dir="/etc/resolver"
  local resolver_file="$resolver_dir/$DOMAIN"

  if [[ -f "$resolver_file" ]]; then
    echo "DNS: *.$DOMAIN already configured."
    return
  fi

  echo "DNS: Configuring wildcard *.$DOMAIN → $IP (macOS resolver)..."
  sudo mkdir -p "$resolver_dir"
  echo "nameserver $IP" | sudo tee "$resolver_file" > /dev/null
  sudo dscacheutil -flushcache
  sudo killall -HUP mDNSResponder 2>/dev/null || true
  echo "DNS: Done."
}

setup_dns_linux() {
  local dnsmasq_conf="/etc/dnsmasq.d/$DOMAIN.conf"
  local conf_line="address=/$DOMAIN/$IP"

  if [[ "${GITHUB_ACTIONS:-}" == "true" ]]; then
    setup_hosts_entries
    return
  fi

  if [[ -f "$dnsmasq_conf" ]] && grep -q "$conf_line" "$dnsmasq_conf" 2>/dev/null; then
    echo "DNS: *.$DOMAIN already configured."
    return
  fi

  if ! command -v dnsmasq &>/dev/null; then
    echo "DNS: Installing dnsmasq..."
    if command -v apt-get &>/dev/null; then
      sudo apt-get update -qq && sudo apt-get install -y -qq dnsmasq
    elif command -v dnf &>/dev/null; then
      sudo dnf install -y -q dnsmasq
    elif command -v pacman &>/dev/null; then
      sudo pacman -S --noconfirm dnsmasq
    else
      echo "Error: could not detect package manager." >&2; exit 1
    fi
  fi

  echo "DNS: Configuring wildcard *.$DOMAIN → $IP (dnsmasq)..."
  sudo mkdir -p /etc/dnsmasq.d
  echo "$conf_line" | sudo tee "$dnsmasq_conf" > /dev/null
  if ! grep -q "^conf-dir=/etc/dnsmasq.d" /etc/dnsmasq.conf 2>/dev/null; then
    echo "conf-dir=/etc/dnsmasq.d/,*.conf" | sudo tee -a /etc/dnsmasq.conf > /dev/null
  fi
  if ! sudo systemctl restart dnsmasq 2>/dev/null && ! sudo service dnsmasq restart 2>/dev/null; then
    echo "DNS: dnsmasq restart unavailable, falling back to explicit host entries..."
    setup_hosts_entries
    return
  fi

  if systemctl is-active --quiet systemd-resolved 2>/dev/null; then
    sudo resolvectl dns lo "$IP" 2>/dev/null || true
    sudo resolvectl domain lo "~$DOMAIN" 2>/dev/null || true
  fi

  echo "DNS: Done."
}

case "$(uname -s)" in
  Darwin) setup_dns_macos ;;
  Linux)  setup_dns_linux ;;
  *)      echo "Unsupported OS: $(uname -s)"; exit 1 ;;
esac

"$SCRIPT_DIR/generate-dev-tls-cert.sh"

echo ""
echo "Verify: ping -c1 auth.$DOMAIN"
echo "Start:  docker compose up -d"
