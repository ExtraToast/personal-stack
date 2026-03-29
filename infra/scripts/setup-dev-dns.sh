#!/usr/bin/env bash
# Sets up wildcard DNS so *.jorisjonkers.test resolves to 127.0.0.1.
# Idempotent — safe to run multiple times.
#
# macOS: uses /etc/resolver/ (built-in wildcard DNS support)
# Linux: installs and configures dnsmasq

set -euo pipefail

DOMAIN="jorisjonkers.test"
IP="127.0.0.1"

setup_macos() {
  local resolver_dir="/etc/resolver"
  local resolver_file="$resolver_dir/$DOMAIN"

  if [[ -f "$resolver_file" ]]; then
    echo "Wildcard DNS for *.$DOMAIN already configured."
    return
  fi

  echo "Configuring wildcard DNS for *.$DOMAIN → $IP (macOS resolver)..."

  if [[ "$EUID" -ne 0 ]]; then
    sudo mkdir -p "$resolver_dir"
    echo "nameserver $IP" | sudo tee "$resolver_file" > /dev/null
  else
    mkdir -p "$resolver_dir"
    echo "nameserver $IP" > "$resolver_file"
  fi

  sudo dscacheutil -flushcache
  sudo killall -HUP mDNSResponder 2>/dev/null || true

  echo "Done. All *.$DOMAIN domains now resolve to $IP."
}

setup_linux() {
  local dnsmasq_conf="/etc/dnsmasq.d/$DOMAIN.conf"
  local conf_line="address=/$DOMAIN/$IP"

  if [[ -f "$dnsmasq_conf" ]] && grep -q "$conf_line" "$dnsmasq_conf" 2>/dev/null; then
    echo "Wildcard DNS for *.$DOMAIN already configured."
    return
  fi

  # Install dnsmasq if not present
  if ! command -v dnsmasq &>/dev/null; then
    echo "Installing dnsmasq..."
    if command -v apt-get &>/dev/null; then
      sudo apt-get update -qq && sudo apt-get install -y -qq dnsmasq
    elif command -v dnf &>/dev/null; then
      sudo dnf install -y -q dnsmasq
    elif command -v pacman &>/dev/null; then
      sudo pacman -S --noconfirm dnsmasq
    else
      echo "Error: could not detect package manager to install dnsmasq." >&2
      exit 1
    fi
  fi

  echo "Configuring wildcard DNS for *.$DOMAIN → $IP (dnsmasq)..."
  sudo mkdir -p /etc/dnsmasq.d
  echo "$conf_line" | sudo tee "$dnsmasq_conf" > /dev/null

  # Ensure dnsmasq reads /etc/dnsmasq.d/ and forwards other queries
  if ! grep -q "^conf-dir=/etc/dnsmasq.d" /etc/dnsmasq.conf 2>/dev/null; then
    echo "conf-dir=/etc/dnsmasq.d/,*.conf" | sudo tee -a /etc/dnsmasq.conf > /dev/null
  fi

  sudo systemctl restart dnsmasq 2>/dev/null || sudo service dnsmasq restart 2>/dev/null

  # Point systemd-resolved at dnsmasq if active
  if systemctl is-active --quiet systemd-resolved 2>/dev/null; then
    # Add dnsmasq as a DNS server for the loopback interface
    sudo resolvectl dns lo "$IP" 2>/dev/null || true
    sudo resolvectl domain lo "~$DOMAIN" 2>/dev/null || true
  fi

  echo "Done. All *.$DOMAIN domains now resolve to $IP."
}

case "$(uname -s)" in
  Darwin) setup_macos ;;
  Linux)  setup_linux ;;
  *)      echo "Unsupported OS: $(uname -s)"; exit 1 ;;
esac

echo ""
echo "Verify: ping -c1 auth.$DOMAIN"
