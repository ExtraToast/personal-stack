#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
cluster_path="${repo_root}/platform/cluster/flux/clusters/production"
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

kustomize build "${cluster_path}" >"${render_output}"

while IFS= read -r chart_file; do
  chart_dir="$(dirname "${chart_file}")"
  release_name="$(basename "${chart_dir}")"
  helm template "${release_name}" "${chart_dir}" >>"${render_output}"
done < <(find "${repo_root}/platform/cluster/flux/apps" -name Chart.yaml | sort)

kubeconform \
  -summary \
  -strict \
  -ignore-missing-schemas \
  "${render_output}"
