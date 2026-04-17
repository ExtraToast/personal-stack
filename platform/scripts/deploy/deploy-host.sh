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

nix_args=(
  run
  github:serokell/deploy-rs
  --
  --skip-checks
  --hostname
  "${deploy_hostname}"
)

if deploy_should_build_on_remote; then
  nix_args+=(--remote-build)
fi

nix_args+=("${flake_ref}#${NODE_NAME}")

run_platform_nix "${nix_args[@]}"
