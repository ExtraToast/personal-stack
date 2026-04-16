#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MANIFEST_FILE="${ROOT_DIR}/backups/manifest.tsv"

require_file() {
  local file="$1"
  [[ -f "${file}" ]] || { echo "Missing file: ${file}" >&2; exit 1; }
}

require_file "${MANIFEST_FILE}"

tmpdir="$(mktemp -d)"
trap 'rm -rf "${tmpdir}"' EXIT

cloud_expected="${tmpdir}/cloud-expected.txt"
home_expected="${tmpdir}/home-expected.txt"
cloud_manifest="${tmpdir}/cloud-manifest.txt"
home_manifest="${tmpdir}/home-manifest.txt"

awk '
  /host_volume "/ {
    getline
    split($0, a, "\"")
    if (a[2] != "") print a[2]
  }
' "${ROOT_DIR}/infra/nomad/configs/nomad.hcl" | sort -u > "${cloud_expected}"

awk '
  /host_volume "/ {
    getline
    split($0, a, "\"")
    if (a[2] != "") print a[2]
  }
' "${ROOT_DIR}/infra/home-node/configs/nomad-client.hcl" | sort -u > "${home_expected}"

awk -F '\t' '$1 == "cloud" && $3 ~ "^/" { print $3 }' "${MANIFEST_FILE}" | sort -u > "${cloud_manifest}"
awk -F '\t' '$1 == "home" && $3 ~ "^/" { print $3 }' "${MANIFEST_FILE}" | sort -u > "${home_manifest}"

echo "Cloud host volumes missing from backups/manifest.tsv:"
comm -23 "${cloud_expected}" "${cloud_manifest}" || true
echo
echo "Home host volumes missing from backups/manifest.tsv:"
comm -23 "${home_expected}" "${home_manifest}" || true
echo
echo "Cloud manifest paths not backed by a declared Nomad host volume:"
comm -13 "${cloud_expected}" "${cloud_manifest}" || true
echo
echo "Home manifest paths not backed by a declared Nomad host volume:"
comm -13 "${home_expected}" "${home_manifest}" || true

missing_count="$(
  {
    comm -23 "${cloud_expected}" "${cloud_manifest}"
    comm -23 "${home_expected}" "${home_manifest}"
  } | awk 'NF { count += 1 } END { print count + 0 }'
)"

if [[ "${missing_count}" -ne 0 ]]; then
  exit 1
fi
