#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/../lib/host-env.sh"

require_single_node_arg "$(basename "$0")" "$@"

load_host_env "$1"
require_host_ssh
require_deploy_authorized_keys_file

cd "$(platform_flake_dir)"
flake_ref="$(platform_flake_ref)"
deploy_hostname="${SSH_HOST}"

if [[ "${NODE_STATUS:-}" == "install-ready" && -n "${BOOTSTRAP_SSH_HOST:-}" ]]; then
  deploy_hostname="${BOOTSTRAP_SSH_HOST}"
fi

# Build a concrete ssh options string from the inventory SSH port and the
# optional identity file so deploy-rs doesn't fall back to ssh-agent enumerating
# every loaded key (which trips sshd MaxAuthTries). This replaces the
# sshOpts=[-p 2222] hardcoded in flake.nix.
ssh_opts=("-p" "${SSH_PORT}")
identity_file="$(platform_ssh_identity_file)"
if [[ -n "${identity_file}" ]]; then
  ssh_opts+=("-o" "IdentitiesOnly=yes" "-i" "${identity_file}")
fi

nix_args=(
  run
  github:serokell/deploy-rs
  --
  --skip-checks
  --hostname
  "${deploy_hostname}"
  --ssh-opts
  "${ssh_opts[*]}"
)

if deploy_should_build_on_remote; then
  nix_args+=(--remote-build)
fi

nix_args+=("${flake_ref}#${NODE_NAME}")

run_platform_nix "${nix_args[@]}"
