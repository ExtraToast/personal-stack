#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Shared inventory resolution keeps shell logic thin and pushes YAML parsing into Kotlin.
source "${script_dir}/../lib/host-env.sh"

require_single_node_arg "$(basename "$0")" "$@"

load_host_env "$1"
require_host_ssh

cd "$(platform_flake_dir)"
"$(platform_nix)" run .#nixos-anywhere -- \
  --flake ".#${NODE_NAME}" \
  --target-host "${SSH_USER}@${SSH_HOST}" \
  --ssh-port "${SSH_PORT}"
