#!/usr/bin/env bash
# Validate every manifest Flux would apply before it hits a cluster.
#
# The previous version only rendered the kustomize overlay plus any
# local Helm charts and then ran kubeconform with -ignore-missing-schemas,
# so Helm-managed CRDs and remote chart values were never actually
# checked. Post-merge failures consistently came from:
#   - remote chart template errors (loki's schema_config, nvidia plugin
#     config shape, SingleBinary + scalable replicas clash),
#   - CRs whose CRD was unknown to kubeconform (MetalLB IPAddressPool,
#     cert-manager Certificate, Flux Kustomization/HelmRelease) which
#     the ignore-missing-schemas flag silently excused.
#
# Now: flux-local expands every HelmRelease against its real chart +
# values, and kubeconform validates the whole rendered tree against an
# explicit CRD catalogue with no ignore flag.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
cluster_path="${repo_root}/platform/cluster/flux/clusters/production"
flux_root="${repo_root}/platform/cluster/flux"
render_output="$(mktemp)"
trap 'rm -f "${render_output}"' EXIT

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

require_command kustomize
require_command helm
require_command kubeconform
require_command flux-local

echo "==> kustomize build ${cluster_path}"
kustomize build "${cluster_path}" > "${render_output}"

echo "==> flux-local expand HelmReleases against remote charts"
# --enable-helm pulls every HelmRelease's chart and renders it with the
# release's spec.values, which is exactly what the cluster's helm-
# controller does post-merge.
flux-local build all \
  --enable-helm \
  "${flux_root}" \
  >> "${render_output}"

# Render any in-repo Chart.yaml directly too (covers anything that
# isn't exposed as a HelmRelease).
while IFS= read -r chart_file; do
  chart_dir="$(dirname "${chart_file}")"
  release_name="$(basename "${chart_dir}")"
  helm template "${release_name}" "${chart_dir}" >> "${render_output}"
done < <(find "${flux_root}/apps" -name Chart.yaml | sort)

echo "==> kubeconform (strict, no ignore-missing-schemas)"
# Explicit schema catalogue per CRD source. Datree's CRDs-catalog mirror
# covers cert-manager, metallb, monitoring.coreos.com, traefik.io and a
# long tail. The fluxcd-community schema repo covers Flux controllers.
# Default (last entry) falls back to built-in Kubernetes schemas.
kubeconform \
  -summary \
  -strict \
  -schema-location default \
  -schema-location 'https://raw.githubusercontent.com/datreeio/CRDs-catalog/main/{{.Group}}/{{.ResourceKind}}_{{.ResourceAPIVersion}}.json' \
  -schema-location 'https://raw.githubusercontent.com/fluxcd-community/flux2-schemas/main/{{.ResourceKind}}-{{.ResourceAPIVersion}}.json' \
  "${render_output}"
