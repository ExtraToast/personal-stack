#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKUP_ROOT="${BACKUP_ROOT:-${ROOT_DIR}/backups}"
MANIFEST_FILE="${BACKUP_MANIFEST_FILE:-${BACKUP_ROOT}/manifest.tsv}"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUTPUT_DIR="${BACKUP_OUTPUT_DIR:-${BACKUP_ROOT}/run-${TIMESTAMP}}"

MODE="apply"
HOST_SCOPE="all"
LIST_ONLY="false"
SERVICE_FILTERS=()

usage() {
  cat <<'EOF' >&2
Usage: backup-service-state.sh [--host cloud|home|all] [--service <service-name>]... [--list] [--dry-run]

Environment per host group (`CLOUD` or `HOME`):
  BACKUP_<GROUP>_SSH_TARGET
  or BACKUP_<GROUP>_SSH_HOST + BACKUP_<GROUP>_SSH_USER
  optional BACKUP_<GROUP>_SSH_PORT (default 22)
  optional BACKUP_<GROUP>_SSH_IDENTITY_FILE
  optional BACKUP_<GROUP>_SSH_OPTS
  optional BACKUP_<GROUP>_SUDO (default: sudo -n)

Examples:
  backup-service-state.sh --list
  backup-service-state.sh --host cloud
  backup-service-state.sh --service stalwart --service uptime-kuma
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

group_upper() {
  printf '%s' "$1" | tr '[:lower:]' '[:upper:]'
}

env_value() {
  local name="$1"
  printf '%s' "${!name:-}"
}

group_target() {
  local group="$1" upper target host user
  upper="$(group_upper "${group}")"

  target="$(env_value "BACKUP_${upper}_SSH_TARGET")"
  if [[ -n "${target}" ]]; then
    printf '%s\n' "${target}"
    return 0
  fi

  host="$(env_value "BACKUP_${upper}_SSH_HOST")"
  user="$(env_value "BACKUP_${upper}_SSH_USER")"

  if [[ -z "${host}" || -z "${user}" ]]; then
    echo "Missing SSH target for ${group}. Set BACKUP_${upper}_SSH_TARGET or BACKUP_${upper}_SSH_HOST/BACKUP_${upper}_SSH_USER." >&2
    exit 1
  fi

  printf '%s@%s\n' "${user}" "${host}"
}

group_port() {
  local group="$1" upper
  upper="$(group_upper "${group}")"
  printf '%s\n' "$(env_value "BACKUP_${upper}_SSH_PORT")"
}

group_identity_file() {
  local group="$1" upper
  upper="$(group_upper "${group}")"
  printf '%s\n' "$(env_value "BACKUP_${upper}_SSH_IDENTITY_FILE")"
}

group_ssh_opts() {
  local group="$1" upper
  upper="$(group_upper "${group}")"
  printf '%s\n' "$(env_value "BACKUP_${upper}_SSH_OPTS")"
}

group_sudo() {
  local group="$1" upper var_name
  upper="$(group_upper "${group}")"
  var_name="BACKUP_${upper}_SUDO"

  if [[ "${!var_name+x}" == x ]]; then
    printf '%s\n' "${!var_name}"
  else
    printf 'sudo -n\n'
  fi
}

SSH_CMD=()
SSH_TARGET=""
REMOTE_SUDO=""

build_ssh_command() {
  local group="$1" port identity extra_opts
  local parsed_opts=()

  SSH_TARGET="$(group_target "${group}")"
  REMOTE_SUDO="$(group_sudo "${group}")"

  SSH_CMD=(ssh)

  port="$(group_port "${group}")"
  if [[ -n "${port}" ]]; then
    SSH_CMD+=(-p "${port}")
  fi

  identity="$(group_identity_file "${group}")"
  if [[ -n "${identity}" ]]; then
    if [[ ! -f "${identity}" ]]; then
      echo "SSH identity file not found: ${identity}" >&2
      exit 1
    fi
    SSH_CMD+=(-i "${identity}")
  fi

  extra_opts="$(group_ssh_opts "${group}")"
  if [[ -n "${extra_opts}" ]]; then
    # shellcheck disable=SC2206
    parsed_opts=(${extra_opts})
    SSH_CMD+=("${parsed_opts[@]}")
  fi
}

remote_exec() {
  local group="$1" remote_command="$2"
  build_ssh_command "${group}"
  "${SSH_CMD[@]}" "${SSH_TARGET}" "bash -lc $(shell_quote "${remote_command}")"
}

matches_filters() {
  local group="$1" service="$2"
  local filter

  if [[ "${HOST_SCOPE}" != "all" && "${group}" != "${HOST_SCOPE}" ]]; then
    return 1
  fi

  if [[ "${#SERVICE_FILTERS[@]}" -eq 0 ]]; then
    return 0
  fi

  for filter in "${SERVICE_FILTERS[@]}"; do
    if [[ "${service}" == "${filter}" ]]; then
      return 0
    fi
  done

  return 1
}

prepare_output_dir() {
  mkdir -p "${OUTPUT_DIR}/cloud" "${OUTPUT_DIR}/home"
  cp "${MANIFEST_FILE}" "${OUTPUT_DIR}/manifest.tsv"
  printf 'generated_at_utc=%s\ngit_revision=%s\n' \
    "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    "$(git -C "${ROOT_DIR}" rev-parse HEAD 2>/dev/null || printf 'unknown')" \
    > "${OUTPUT_DIR}/run-metadata.txt"
  printf 'host_group\tservice_name\tsource_path\tarchive_path\tstatus\tsize_bytes\tdescription\n' > "${OUTPUT_DIR}/archives.tsv"
  : > "${OUTPUT_DIR}/checksums.sha256"
}

record_archive() {
  local group="$1" service="$2" source_path="$3" archive_path="$4" status="$5" description="$6"
  local checksum="" size_bytes=""

  if [[ -f "${archive_path}" ]]; then
    checksum="$(sha256_file "${archive_path}")"
    size_bytes="$(wc -c < "${archive_path}" | tr -d ' ')"
    printf '%s  %s\n' "${checksum}" "${group}/$(basename "${archive_path}")" >> "${OUTPUT_DIR}/checksums.sha256"
  fi

  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "${group}" \
    "${service}" \
    "${source_path}" \
    "${archive_path}" \
    "${status}" \
    "${size_bytes}" \
    "${description}" \
    >> "${OUTPUT_DIR}/archives.tsv"
}

backup_entry() {
  local group="$1" service="$2" source_path="$3" required="$4" description="$5"
  local relative_path archive_path remote_path_quoted relative_path_quoted remote_sudo

  if ! matches_filters "${group}" "${service}"; then
    return 0
  fi

  relative_path="${source_path#/}"
  archive_path="${OUTPUT_DIR}/${group}/${service}.tar.gz"
  remote_path_quoted="$(shell_quote "${source_path}")"
  relative_path_quoted="$(shell_quote "${relative_path}")"
  remote_sudo="$(group_sudo "${group}")"

  if ! remote_exec "${group}" "${remote_sudo} test -e ${remote_path_quoted}" >/dev/null 2>&1; then
    if [[ "${required}" == "true" ]]; then
      echo "Required path missing on ${group}: ${source_path} (${service})" >&2
      return 1
    fi

    echo "Skipping optional path on ${group}: ${source_path} (${service})" >&2
    if [[ "${MODE}" == "apply" ]]; then
      record_archive "${group}" "${service}" "${source_path}" "" "missing-optional" "${description}"
    fi
    return 0
  fi

  echo "Backing up ${group}:${service} from ${source_path}"

  if [[ "${MODE}" == "dry-run" ]]; then
    return 0
  fi

  remote_exec "${group}" "${remote_sudo} tar --numeric-owner --acls --xattrs -C / -cpf - ${relative_path_quoted}" \
    | gzip -1 > "${archive_path}"

  record_archive "${group}" "${service}" "${source_path}" "${archive_path}" "backed-up" "${description}"
}

list_manifest() {
  local group service source_path required description

  while IFS=$'\t' read -r group service source_path required description; do
    [[ -z "${group}" || "${group}" == \#* ]] && continue
    if matches_filters "${group}" "${service}"; then
      printf '%-5s %-24s %-40s %-5s %s\n' "${group}" "${service}" "${source_path}" "${required}" "${description}"
    fi
  done < "${MANIFEST_FILE}"
}

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --host)
      shift
      [[ "$#" -gt 0 ]] || usage
      HOST_SCOPE="$1"
      ;;
    --service)
      shift
      [[ "$#" -gt 0 ]] || usage
      SERVICE_FILTERS+=("$1")
      ;;
    --dry-run)
      MODE="dry-run"
      ;;
    --list)
      LIST_ONLY="true"
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

[[ -f "${MANIFEST_FILE}" ]] || {
  echo "Backup manifest not found: ${MANIFEST_FILE}" >&2
  exit 1
}

case "${HOST_SCOPE}" in
  all|cloud|home) ;;
  *)
    echo "Unsupported host scope: ${HOST_SCOPE}" >&2
    exit 1
    ;;
esac

if [[ "${LIST_ONLY}" == "true" ]]; then
  list_manifest
  exit 0
fi

if [[ "${MODE}" == "apply" ]]; then
  prepare_output_dir
fi

while IFS=$'\t' read -r group service source_path required description; do
  [[ -z "${group}" || "${group}" == \#* ]] && continue
  backup_entry "${group}" "${service}" "${source_path}" "${required}" "${description}"
done < "${MANIFEST_FILE}"

if [[ "${MODE}" == "apply" ]]; then
  echo "Backups written to ${OUTPUT_DIR}"
else
  echo "Dry run complete"
fi
