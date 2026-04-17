#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/../lib/host-env.sh"

usage() {
  echo "Usage: [PLATFORM_PI_IMAGE_OUT_LINK=<out-link>] $(basename "$0") <node-name>" >&2
  exit 1
}

require_single_node_arg "$(basename "$0")" "$@"

load_host_env "$1"

if [[ "${NODE_ARCH:-}" != "arm64" ]]; then
  echo "Node ${NODE_NAME} is ${NODE_ARCH:-unknown}, not an arm64 Raspberry Pi image target" >&2
  exit 1
fi

current_system="$(platform_current_system)"
if [[ "${current_system}" != "${NIX_SYSTEM:-}" ]]; then
  echo "Building ${NODE_NAME} produces ${NIX_SYSTEM}. Configure a matching Nix builder if this machine cannot build that platform locally." >&2
fi

cd "$(platform_flake_dir)"
flake_ref="$(platform_flake_ref)"
out_link="${PLATFORM_PI_IMAGE_OUT_LINK:-result-${NODE_NAME}-sd-image}"

nix_args=(
  build
  "${flake_ref}#piSdImages.${NODE_NAME}"
  --out-link
  "${out_link}"
  --print-build-logs
)

run_platform_nix "${nix_args[@]}"

echo "Built Raspberry Pi SD image for ${NODE_NAME} at ${out_link}"
if [[ -d "${out_link}/sd-image" ]]; then
  find -L "${out_link}/sd-image" -maxdepth 1 -type f | sort
fi
