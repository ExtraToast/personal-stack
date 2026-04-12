#!/usr/bin/env bash
# nomad-advertise.sh — Generate /etc/nomad.d/advertise.hcl with the best
# routable IP.  Runs as ExecStartPre before Nomad so the advertise address
# is always correct, even when Tailscale isn't up yet (e.g. fresh boot
# before Headscale is scheduled).
#
# Priority: Tailscale IP > primary outbound IP (never Docker bridge).
set -euo pipefail

advertise_ip=""

# Prefer Tailscale if the daemon is running and has an IPv4 address
if command -v tailscale >/dev/null 2>&1 && tailscale status >/dev/null 2>&1; then
  advertise_ip="$(tailscale ip -4 2>/dev/null || true)"
fi

# Fallback: the IP the kernel would use to reach the internet.
# This is always the "real" interface, never the Docker bridge.
if [[ -z "${advertise_ip}" ]]; then
  advertise_ip="$(ip -4 route get 1.1.1.1 2>/dev/null | awk '/src/ {print $7; exit}' || true)"
fi

if [[ -z "${advertise_ip}" ]]; then
  echo "nomad-advertise: could not determine advertise IP, skipping" >&2
  exit 0
fi

cat > /etc/nomad.d/advertise.hcl <<EOF
advertise {
  http = "${advertise_ip}"
  rpc  = "${advertise_ip}"
  serf = "${advertise_ip}"
}
EOF

echo "nomad-advertise: ${advertise_ip}"