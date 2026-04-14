#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
gradlew_path="${PLATFORM_GRADLEW:-${repo_root}/gradlew}"
output_path="${1:-${repo_root}/platform/cluster/flux/apps/edge/edge-catalog-configmap.yaml}"

catalog="$("${gradlew_path}" -q :platform:tooling:run --args="render-edge-catalog")"

mkdir -p "$(dirname "${output_path}")"

{
  cat <<'EOF'
apiVersion: v1
kind: ConfigMap
metadata:
  name: platform-edge-catalog
  namespace: edge-system
data:
  edge-catalog.yaml: |
EOF
  while IFS= read -r line; do
    printf '    %s\n' "${line}"
  done <<< "${catalog}"
} > "${output_path}"
