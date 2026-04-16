#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKUP_ROOT="${BACKUP_ROOT:-${ROOT_DIR}/backups}"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUTPUT_DIR="${BACKUP_OUTPUT_DIR:-${BACKUP_ROOT}/run-${TIMESTAMP}}"

MODE="apply"
LIST_ONLY="false"
ARTIFACT_FILTERS=()

usage() {
  cat <<'EOF' >&2
Usage: backup-service-snapshots.sh [--artifact <artifact-name>]... [--list] [--dry-run]

Environment:
  BACKUP_CLOUD_SSH_TARGET
  or BACKUP_CLOUD_SSH_HOST + BACKUP_CLOUD_SSH_USER
  optional BACKUP_CLOUD_SSH_PORT (default 22)
  optional BACKUP_CLOUD_SSH_IDENTITY_FILE
  optional BACKUP_CLOUD_SSH_OPTS
  optional BACKUP_CLOUD_SUDO (default: sudo -n)

Examples:
  backup-service-snapshots.sh --list
  BACKUP_OUTPUT_DIR="$PWD/backups/run-$(date -u +%Y%m%dT%H%M%SZ)" backup-service-snapshots.sh
  backup-service-snapshots.sh --artifact vault-raft-snapshot --artifact nomad-snapshot
EOF
  exit 1
}

shell_quote() {
  printf "'%s'" "${1//\'/\'\"\'\"\'}"
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

env_value() {
  local name="$1"
  printf '%s' "${!name:-}"
}

group_target() {
  local target host user

  target="$(env_value "BACKUP_CLOUD_SSH_TARGET")"
  if [[ -n "${target}" ]]; then
    printf '%s\n' "${target}"
    return 0
  fi

  host="$(env_value "BACKUP_CLOUD_SSH_HOST")"
  user="$(env_value "BACKUP_CLOUD_SSH_USER")"

  if [[ -z "${host}" || -z "${user}" ]]; then
    echo "Missing SSH target for cloud. Set BACKUP_CLOUD_SSH_TARGET or BACKUP_CLOUD_SSH_HOST/BACKUP_CLOUD_SSH_USER." >&2
    exit 1
  fi

  printf '%s@%s\n' "${user}" "${host}"
}

group_sudo() {
  if [[ "${BACKUP_CLOUD_SUDO+x}" == x ]]; then
    printf '%s\n' "${BACKUP_CLOUD_SUDO}"
  else
    printf 'sudo -n\n'
  fi
}

SSH_CMD=()
SSH_TARGET=""

build_ssh_command() {
  local port identity extra_opts
  local parsed_opts=()

  SSH_TARGET="$(group_target)"
  SSH_CMD=(ssh)

  port="$(env_value "BACKUP_CLOUD_SSH_PORT")"
  if [[ -n "${port}" ]]; then
    SSH_CMD+=(-p "${port}")
  fi

  identity="$(env_value "BACKUP_CLOUD_SSH_IDENTITY_FILE")"
  if [[ -n "${identity}" ]]; then
    if [[ ! -f "${identity}" ]]; then
      echo "SSH identity file not found: ${identity}" >&2
      exit 1
    fi
    SSH_CMD+=(-i "${identity}")
  fi

  extra_opts="$(env_value "BACKUP_CLOUD_SSH_OPTS")"
  if [[ -n "${extra_opts}" ]]; then
    # shellcheck disable=SC2206
    parsed_opts=(${extra_opts})
    SSH_CMD+=("${parsed_opts[@]}")
  fi
}

remote_stream() {
  local remote_command="$1"
  build_ssh_command
  "${SSH_CMD[@]}" "${SSH_TARGET}" "bash -lc $(shell_quote "${remote_command}")"
}

matches_filters() {
  local artifact="$1"
  local filter

  if [[ "${#ARTIFACT_FILTERS[@]}" -eq 0 ]]; then
    return 0
  fi

  for filter in "${ARTIFACT_FILTERS[@]}"; do
    if [[ "${artifact}" == "${filter}" ]]; then
      return 0
    fi
  done

  return 1
}

prepare_output_dir() {
  mkdir -p "${OUTPUT_DIR}/cloud"
  printf 'generated_at_utc=%s\ngit_revision=%s\n' \
    "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    "$(git -C "${ROOT_DIR}" rev-parse HEAD 2>/dev/null || printf 'unknown')" \
    > "${OUTPUT_DIR}/run-metadata.txt"
  printf 'artifact\toutput_path\tstatus\tsize_bytes\tdescription\n' > "${OUTPUT_DIR}/service-snapshots.tsv"
  : > "${OUTPUT_DIR}/service-snapshots.sha256"
}

record_artifact() {
  local artifact="$1" output_path="$2" status="$3" description="$4"
  local checksum="" size_bytes=""

  if [[ -f "${output_path}" ]]; then
    checksum="$(sha256_file "${output_path}")"
    size_bytes="$(wc -c < "${output_path}" | tr -d ' ')"
    printf '%s  %s\n' "${checksum}" "cloud/$(basename "${output_path}")" >> "${OUTPUT_DIR}/service-snapshots.sha256"
  fi

  printf '%s\t%s\t%s\t%s\t%s\n' \
    "${artifact}" \
    "${output_path}" \
    "${status}" \
    "${size_bytes}" \
    "${description}" \
    >> "${OUTPUT_DIR}/service-snapshots.tsv"
}

capture_artifact() {
  local artifact="$1" filename="$2" description="$3" remote_command="$4"
  local output_path="${OUTPUT_DIR}/cloud/${filename}"

  if ! matches_filters "${artifact}"; then
    return 0
  fi

  echo "Capturing ${artifact}"

  if [[ "${MODE}" == "dry-run" ]]; then
    return 0
  fi

  remote_stream "${remote_command}" > "${output_path}"
  record_artifact "${artifact}" "${output_path}" "captured" "${description}"
}

list_artifacts() {
  cat <<'EOF'
vault-raft-snapshot	Vault integrated storage snapshot
consul-snapshot	Consul server snapshot including KV/catalog/sessions/ACLs
nomad-snapshot	Nomad server snapshot including jobs/nodes/allocations/ACLs
nomad-job-status	Readable live Nomad job status inventory
rabbitmq-definitions	RabbitMQ cluster definitions export
EOF
}

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --artifact)
      ARTIFACT_FILTERS+=("${2:?Missing value for --artifact}")
      shift
      ;;
    --list)
      LIST_ONLY="true"
      ;;
    --dry-run)
      MODE="dry-run"
      ;;
    -h|--help)
      usage
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      ;;
  esac
  shift
done

if [[ "${LIST_ONLY}" == "true" ]]; then
  list_artifacts
  exit 0
fi

prepare_output_dir

REMOTE_SUDO="$(group_sudo)"

capture_artifact \
  "vault-raft-snapshot" \
  "vault-raft.snapshot" \
  "Vault integrated storage snapshot" \
  "${REMOTE_SUDO} bash -lc 'set -euo pipefail; source /opt/personal-stack/.vault-keys; export VAULT_ADDR=http://127.0.0.1:8200 VAULT_TOKEN=\"\$VAULT_ROOT_TOKEN\"; tmp=\$(mktemp -d); trap \"rm -rf \\\"\$tmp\\\"\" EXIT; vault operator raft snapshot save \"\$tmp/vault.snap\" >/dev/null; cat \"\$tmp/vault.snap\"'"

capture_artifact \
  "consul-snapshot" \
  "consul.snapshot" \
  "Consul atomic point-in-time snapshot" \
  "${REMOTE_SUDO} bash -lc 'set -euo pipefail; export CONSUL_HTTP_ADDR=http://127.0.0.1:8500; tmp=\$(mktemp -d); trap \"rm -rf \\\"\$tmp\\\"\" EXIT; consul snapshot save \"\$tmp/consul.snap\" >/dev/null; cat \"\$tmp/consul.snap\"'"

capture_artifact \
  "nomad-snapshot" \
  "nomad.snapshot" \
  "Nomad atomic point-in-time snapshot" \
  "${REMOTE_SUDO} bash -lc 'set -euo pipefail; source /opt/personal-stack/.nomad-keys; export NOMAD_ADDR=http://127.0.0.1:4646 NOMAD_TOKEN=\"\$NOMAD_BOOTSTRAP_TOKEN\"; tmp=\$(mktemp -d); trap \"rm -rf \\\"\$tmp\\\"\" EXIT; nomad operator snapshot save \"\$tmp/nomad.snap\" >/dev/null; cat \"\$tmp/nomad.snap\"'"

capture_artifact \
  "nomad-job-status" \
  "nomad-job-status.json" \
  "Live Nomad job status export" \
  "${REMOTE_SUDO} bash -lc 'set -euo pipefail; source /opt/personal-stack/.nomad-keys; export NOMAD_ADDR=http://127.0.0.1:4646 NOMAD_TOKEN=\"\$NOMAD_BOOTSTRAP_TOKEN\"; nomad job status -json'"

capture_artifact \
  "rabbitmq-definitions" \
  "rabbitmq-definitions.json" \
  "RabbitMQ schema, users, vhosts, queues, exchanges, bindings, and runtime parameters" \
  "${REMOTE_SUDO} bash -lc 'set -euo pipefail; source /opt/personal-stack/.vault-keys; export VAULT_ADDR=http://127.0.0.1:8200 VAULT_TOKEN=\"\$VAULT_ROOT_TOKEN\"; rmq_user=\$(vault kv get -field=rabbitmq.user secret/platform/rabbitmq); rmq_password=\$(vault kv get -field=rabbitmq.password secret/platform/rabbitmq); curl -fsS --user \"\$rmq_user:\$rmq_password\" http://127.0.0.1:15672/api/definitions'"

echo "Service-native snapshots written to ${OUTPUT_DIR}"
