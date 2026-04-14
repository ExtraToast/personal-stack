#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

require_command nix
require_command kustomize
require_command helm
require_command kubeconform
require_command git

nix flake check "${repo_root}/platform"
"${repo_root}/platform/scripts/render/render-edge-catalog-configmap.sh"
"${repo_root}/platform/scripts/render/render-edge-route-catalog-configmap.sh"
"${repo_root}/platform/scripts/render/render-traefik-ingressroutes.sh"
(
  cd "${repo_root}"
  git diff --exit-code -- platform/cluster/flux/apps/edge/edge-catalog-configmap.yaml
  git diff --exit-code -- platform/cluster/flux/apps/edge/edge-route-catalog-configmap.yaml
  git diff --exit-code -- platform/cluster/flux/apps/edge/traefik-ingressroutes.yaml
)
# Delegates manifest validation to render-flux.sh, which runs:
# - kustomize build
# - helm template
# - kubeconform
"${script_dir}/render-flux.sh"
