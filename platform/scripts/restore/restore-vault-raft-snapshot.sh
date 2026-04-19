#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF' >&2
Usage: restore-vault-raft-snapshot.sh --snapshot <vault-raft.snapshot> [options]

Restores a Vault raft snapshot into the running Vault pod.

Preconditions:
  - the Vault pod already exists
  - Vault is initialized and unsealed
  - VAULT_TOKEN is set locally, or pass --vault-token

Options:
  --snapshot <file>      Local Vault raft snapshot file.
  --namespace <ns>       Namespace containing Vault. Default: data-system.
  --pod <name>           Vault pod name. Default: vault-0.
  --container <name>     Container name. Default: vault.
  --vault-token <token>  Vault token to use for the restore.
EOF
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing command: $1" >&2
    exit 1
  }
}

SNAPSHOT=""
NAMESPACE="data-system"
POD_NAME="vault-0"
CONTAINER_NAME="vault"
VAULT_TOKEN_VALUE="${VAULT_TOKEN:-}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --snapshot)
      SNAPSHOT="${2:?Missing value for --snapshot}"
      shift 2
      ;;
    --namespace)
      NAMESPACE="${2:?Missing value for --namespace}"
      shift 2
      ;;
    --pod)
      POD_NAME="${2:?Missing value for --pod}"
      shift 2
      ;;
    --container)
      CONTAINER_NAME="${2:?Missing value for --container}"
      shift 2
      ;;
    --vault-token)
      VAULT_TOKEN_VALUE="${2:?Missing value for --vault-token}"
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

require_command kubectl

[[ -n "${SNAPSHOT}" ]] || usage
[[ -f "${SNAPSHOT}" ]] || {
  echo "Snapshot not found: ${SNAPSHOT}" >&2
  exit 1
}
[[ -n "${VAULT_TOKEN_VALUE}" ]] || {
  echo "Vault token is required. Set VAULT_TOKEN or pass --vault-token." >&2
  exit 1
}

kubectl get pod -n "${NAMESPACE}" "${POD_NAME}" >/dev/null
kubectl wait -n "${NAMESPACE}" --for=condition=Ready "pod/${POD_NAME}" --timeout=180s >/dev/null

echo "Restoring Vault raft snapshot from ${SNAPSHOT}"
kubectl exec -i -n "${NAMESPACE}" -c "${CONTAINER_NAME}" "${POD_NAME}" -- \
  env VAULT_ADDR=http://127.0.0.1:8200 VAULT_TOKEN="${VAULT_TOKEN_VALUE}" \
  sh -ec 'cat >/tmp/vault.snap && vault operator raft snapshot restore -force /tmp/vault.snap && rm -f /tmp/vault.snap' \
  < "${SNAPSHOT}"

echo "Vault raft snapshot restore finished"
