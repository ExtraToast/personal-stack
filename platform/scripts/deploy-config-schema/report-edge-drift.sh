#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
work_dir="${DEPLOY_CONFIG_SCHEMA_WORK_DIR:-/tmp/personal-stack-deploy-config-schema}"
cli="${DEPLOY_CONFIG_SCHEMA_BIN:-deploy-config-schema}"

mkdir -p "${work_dir}"

DEPLOY_CONFIG_SCHEMA_LOCAL="${DEPLOY_CONFIG_SCHEMA_LOCAL:-}" \
  node "${script_dir}/fleet-to-deploy-config.mjs" "${repo_root}/platform/inventory/fleet.yaml" \
  > "${work_dir}/deploy-config.json"

run_cli() {
  # DEPLOY_CONFIG_SCHEMA_BIN may be a command string for local checkout tests.
  # CI uses the installed deploy-config-schema binary.
  ${cli} "$@"
}

run_cli validate deploy-config "${work_dir}/deploy-config.json" >/dev/null

render() {
  local adapter="$1"
  run_cli render "${adapter}" "${work_dir}/deploy-config.json" --output "${work_dir}/${adapter}.yaml"
}

render edge-catalog
render edge-route-catalog
render gatus
render traefik-public
render traefik-lan

drift_found=0

report() {
  local label="$1"
  local generated="$2"
  local committed="$3"

  if cmp -s "${generated}" "${committed}"; then
    echo "MATCH ${label}"
  else
    echo "DIFF  ${label}: ${generated} vs ${committed}"
    drift_found=1
  fi
}

report edge-catalog "${work_dir}/edge-catalog.yaml" "${repo_root}/platform/cluster/flux/apps/edge/edge-catalog-configmap.yaml"
report edge-route-catalog "${work_dir}/edge-route-catalog.yaml" "${repo_root}/platform/cluster/flux/apps/edge/edge-route-catalog-configmap.yaml"
report gatus "${work_dir}/gatus.yaml" "${repo_root}/platform/cluster/flux/apps/observability/gatus/gatus-endpoints-configmap.yaml"
report traefik-public "${work_dir}/traefik-public.yaml" "${repo_root}/platform/cluster/flux/apps/edge/traefik-ingressroutes.yaml"
report traefik-lan "${work_dir}/traefik-lan.yaml" "${repo_root}/platform/cluster/flux/apps/edge/traefik-lan-ingressroutes.yaml"

# Report-only by default. When DRIFT_GATE=1 (CI), fail on any drift so the
# toolkit's render output is locked to the committed tree.
if [ "${DRIFT_GATE:-0}" = "1" ] && [ "${drift_found}" = "1" ]; then
  echo "Edge render drift detected: toolkit output diverges from the committed tree." >&2
  exit 1
fi
