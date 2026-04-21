#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESTORE_PVC_SCRIPT="${SCRIPT_DIR}/restore-pvc-archive.sh"

usage() {
  cat <<'EOF' >&2
Usage: restore-k3s-pvcs.sh --backup-dir <backups/run-YYYYMMDD>

Restores the fixed-name PVC-backed workloads from a legacy backup run:
  - postgres-data
  - rabbitmq-data
  - valkey-data
  - n8n-data
  - stalwart-data

The target PVCs must already exist in the cluster, and the workloads should stay
scaled down until the restore completes.
EOF
  exit 1
}

BACKUP_DIR=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --backup-dir)
      BACKUP_DIR="${2:?Missing value for --backup-dir}"
      shift 2
      ;;
    -h|--help)
      usage
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      ;;
  esac
done

[[ -n "${BACKUP_DIR}" ]] || usage
[[ -d "${BACKUP_DIR}" ]] || {
  echo "Backup directory not found: ${BACKUP_DIR}" >&2
  exit 1
}

"${RESTORE_PVC_SCRIPT}" --namespace data-system --pvc postgres-data --archive "${BACKUP_DIR}/cloud/postgres.tar.gz" --strip-components 3 --wipe-target
"${RESTORE_PVC_SCRIPT}" --namespace data-system --pvc rabbitmq-data --archive "${BACKUP_DIR}/cloud/rabbitmq.tar.gz" --strip-components 3 --wipe-target
"${RESTORE_PVC_SCRIPT}" --namespace data-system --pvc valkey-data --archive "${BACKUP_DIR}/cloud/valkey.tar.gz" --strip-components 3 --wipe-target
"${RESTORE_PVC_SCRIPT}" --namespace automation-system --pvc n8n-data --archive "${BACKUP_DIR}/cloud/n8n.tar.gz" --strip-components 3 --wipe-target
"${RESTORE_PVC_SCRIPT}" --namespace mail-system --pvc stalwart-data --archive "${BACKUP_DIR}/cloud/stalwart.tar.gz" --strip-components 3 --wipe-target

echo "Fixed-name PVC restores completed from ${BACKUP_DIR}"
