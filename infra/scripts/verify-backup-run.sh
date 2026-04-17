#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MANIFEST_FILE="${ROOT_DIR}/backups/manifest.tsv"

usage() {
  cat <<'EOF' >&2
Usage: verify-backup-run.sh <run-dir>

Checks that:
  - every required manifest entry is present in archives.tsv with status backed-up
  - every required service-native snapshot/export was captured
  - recorded SHA-256 files still match
EOF
  exit 1
}

sha256_file() {
  local file="$1"

  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "${file}" | awk '{print $1}'
    return 0
  fi

  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "${file}" | awk '{print $1}'
    return 0
  fi

  echo "Missing shasum/sha256sum for checksum generation" >&2
  exit 1
}

[[ "$#" -eq 1 ]] || usage

RUN_DIR="$1"
ARCHIVES_TSV="${RUN_DIR}/archives.tsv"
CHECKSUMS_FILE="${RUN_DIR}/checksums.sha256"
SNAPSHOTS_TSV="${RUN_DIR}/service-snapshots.tsv"
SNAPSHOT_CHECKSUMS_FILE="${RUN_DIR}/service-snapshots.sha256"

[[ -d "${RUN_DIR}" ]] || { echo "Run directory not found: ${RUN_DIR}" >&2; exit 1; }
[[ -f "${MANIFEST_FILE}" ]] || { echo "Missing manifest: ${MANIFEST_FILE}" >&2; exit 1; }
[[ -f "${ARCHIVES_TSV}" ]] || { echo "Missing archives.tsv in ${RUN_DIR}" >&2; exit 1; }
[[ -f "${CHECKSUMS_FILE}" ]] || { echo "Missing checksums.sha256 in ${RUN_DIR}" >&2; exit 1; }
[[ -f "${SNAPSHOTS_TSV}" ]] || { echo "Missing service-snapshots.tsv in ${RUN_DIR}" >&2; exit 1; }
[[ -f "${SNAPSHOT_CHECKSUMS_FILE}" ]] || { echo "Missing service-snapshots.sha256 in ${RUN_DIR}" >&2; exit 1; }

failures=0

while IFS=$'\t' read -r group service source_path required description; do
  [[ -z "${group}" || "${group}" == \#* ]] && continue
  [[ "${required}" == "true" ]] || continue

  status="$(
    awk -F '\t' -v g="${group}" -v s="${service}" '
      $1 == g && $2 == s { print $5; exit }
    ' "${ARCHIVES_TSV}"
  )"

  if [[ "${status}" != "backed-up" ]]; then
    echo "Missing required archive: ${group}/${service} (${source_path})" >&2
    failures=$((failures + 1))
  fi
done < "${MANIFEST_FILE}"

for artifact in \
  vault-raft-snapshot \
  consul-snapshot \
  nomad-snapshot \
  nomad-job-status \
  rabbitmq-definitions; do
  status="$(
    awk -F '\t' -v name="${artifact}" '
      $1 == name { print $3; exit }
    ' "${SNAPSHOTS_TSV}"
  )"

  if [[ "${status}" != "captured" ]]; then
    echo "Missing required service snapshot/export: ${artifact}" >&2
    failures=$((failures + 1))
  fi
done

while read -r checksum relative_path; do
  [[ -n "${checksum}" ]] || continue
  target="${RUN_DIR}/${relative_path}"
  if [[ ! -f "${target}" ]]; then
    echo "Missing checksummed archive file: ${target}" >&2
    failures=$((failures + 1))
    continue
  fi

  actual="$(sha256_file "${target}")"
  if [[ "${actual}" != "${checksum}" ]]; then
    echo "Checksum mismatch: ${target}" >&2
    failures=$((failures + 1))
  fi
done < "${CHECKSUMS_FILE}"

while read -r checksum relative_path; do
  [[ -n "${checksum}" ]] || continue
  target="${RUN_DIR}/${relative_path}"
  if [[ ! -f "${target}" ]]; then
    echo "Missing checksummed snapshot file: ${target}" >&2
    failures=$((failures + 1))
    continue
  fi

  actual="$(sha256_file "${target}")"
  if [[ "${actual}" != "${checksum}" ]]; then
    echo "Checksum mismatch: ${target}" >&2
    failures=$((failures + 1))
  fi
done < "${SNAPSHOT_CHECKSUMS_FILE}"

if [[ "${failures}" -ne 0 ]]; then
  echo "Backup verification failed with ${failures} issue(s)." >&2
  exit 1
fi

echo "Backup verification passed for ${RUN_DIR}"
