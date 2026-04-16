#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/../lib/host-env.sh"

require_single_node_arg "$(basename "$0")" "$@"

load_host_env "$1"
require_host_ssh

cd "$(platform_flake_dir)"
flake_ref="$(platform_flake_ref)"
nix_args=(
  run
  github:serokell/deploy-rs
  --
  --skip-checks
)

if deploy_should_build_on_remote; then
  nix_args+=(--remote-build)
fi

nix_args+=("${flake_ref}#${NODE_NAME}")

run_platform_nix "${nix_args[@]}"
