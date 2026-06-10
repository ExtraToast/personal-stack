#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
output_path="${1:-${repo_root}/platform/cluster/flux/apps/edge/traefik-lan-ingressroutes.yaml}"

# shellcheck source=platform/scripts/render/_toolkit-render.sh
source "${script_dir}/_toolkit-render.sh"

render_adapter traefik-lan "${output_path}" "${repo_root}"
