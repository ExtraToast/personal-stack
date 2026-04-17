#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
output_path="${1:-${repo_root}/platform/cluster/flux/apps/edge/traefik-lan-ingressroutes.yaml}"
gradlew_path="${PLATFORM_GRADLEW:-${repo_root}/gradlew}"

mkdir -p "$(dirname "${output_path}")"
"${gradlew_path}" -q :platform:tooling:run --args="render-traefik-lan-ingressroutes" > "${output_path}"
