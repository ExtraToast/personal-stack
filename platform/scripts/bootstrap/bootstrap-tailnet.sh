#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/../lib/host-env.sh"

usage() {
  echo "Usage: TS_AUTH_KEY=<tailscale-auth-key> [PLATFORM_SSH_IDENTITY_FILE=<identity-file>] $(basename "$0") <node-name>" >&2
  exit 1
}

require_single_node_arg "$(basename "$0")" "$@"

if [[ -z "${TS_AUTH_KEY:-}" ]]; then
  echo "Missing TS_AUTH_KEY; generate a one-off auth key in the Tailscale admin console first" >&2
  usage
fi

load_host_env "$1"
require_host_ssh
require_platform_ssh_identity_file_if_set

ssh_args=(
  ssh
  -p
  "${SSH_PORT}"
)

if [[ -n "$(platform_ssh_identity_file)" ]]; then
  # Pair -i with IdentitiesOnly=yes so ssh-agent doesn't offer every loaded
  # key first and trip the remote sshd's MaxAuthTries limit.
  ssh_args+=(
    -o
    IdentitiesOnly=yes
    -i
    "$(platform_ssh_identity_file)"
  )
fi

ssh_host="${SSH_HOST}"
if [[ "${HAS_BOOTSTRAP_SSH:-false}" == "true" && -n "${BOOTSTRAP_SSH_HOST:-}" ]]; then
  # Before the first tailnet join the steady-state hostname may not resolve yet.
  # Reuse the current LAN/bootstrap address but keep the NixOS SSH user and port.
  ssh_host="${BOOTSTRAP_SSH_HOST}"
fi

ssh_target="${SSH_USER}@${ssh_host}"

printf '%s\n' "${TS_AUTH_KEY}" |
  "${ssh_args[@]}" "${ssh_target}" '
    read -r TS_AUTH_KEY
    sudo systemctl enable --now tailscaled >/dev/null
    sudo env TS_AUTH_KEY="${TS_AUTH_KEY}" tailscale up \
      --auth-key="${TS_AUTH_KEY}" \
      --hostname="'"${NODE_NAME}"'" \
      --accept-dns=true
  '

"${ssh_args[@]}" "${ssh_target}" "tailscale status"
echo "Tailnet bootstrap completed for ${NODE_NAME}"
