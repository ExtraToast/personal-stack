#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/../lib/host-env.sh"

require_single_node_arg "$(basename "$0")" "$@"

load_host_env "$1"
require_host_ssh

cd "$(platform_flake_dir)"
run_platform_nix run github:serokell/deploy-rs -- ".#${NODE_NAME}"
