#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF' >&2
Usage: restore-hostpath-archive.sh --ssh-target <user@host> --target-path <dir> --archive <file.tar.gz> [options]

Streams a local .tar.gz archive into a remote host path over SSH.

Options:
  --ssh-target <user@host>      Remote SSH target.
  --ssh-port <port>             SSH port. Default: 22.
  --identity-file <path>        SSH identity file.
  --ssh-opts "<opts>"           Extra SSH options, shell-split.
  --sudo "<cmd>"                Remote privilege wrapper. Default: sudo -n.
  --target-path <dir>           Host path to restore into.
  --archive <file>              Local .tar.gz archive to restore.
  --strip-components <n>        Tar strip count before writing into the target root. Default: 0.
  --wipe-target                 Delete existing target contents before extraction.
EOF
  exit 1
}

shell_quote() {
  printf "'%s'" "${1//\'/\'\"\'\"\'}"
}

SSH_TARGET=""
SSH_PORT=22
IDENTITY_FILE=""
SSH_OPTS=""
REMOTE_SUDO="sudo -n"
TARGET_PATH=""
ARCHIVE=""
STRIP_COMPONENTS=0
WIPE_TARGET="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --ssh-target)
      SSH_TARGET="${2:?Missing value for --ssh-target}"
      shift 2
      ;;
    --ssh-port)
      SSH_PORT="${2:?Missing value for --ssh-port}"
      shift 2
      ;;
    --identity-file)
      IDENTITY_FILE="${2:?Missing value for --identity-file}"
      shift 2
      ;;
    --ssh-opts)
      SSH_OPTS="${2:?Missing value for --ssh-opts}"
      shift 2
      ;;
    --sudo)
      REMOTE_SUDO="${2:?Missing value for --sudo}"
      shift 2
      ;;
    --target-path)
      TARGET_PATH="${2:?Missing value for --target-path}"
      shift 2
      ;;
    --archive)
      ARCHIVE="${2:?Missing value for --archive}"
      shift 2
      ;;
    --strip-components)
      STRIP_COMPONENTS="${2:?Missing value for --strip-components}"
      shift 2
      ;;
    --wipe-target)
      WIPE_TARGET="true"
      shift
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

[[ -n "${SSH_TARGET}" ]] || usage
[[ -n "${TARGET_PATH}" ]] || usage
[[ -n "${ARCHIVE}" ]] || usage
[[ -f "${ARCHIVE}" ]] || {
  echo "Archive not found: ${ARCHIVE}" >&2
  exit 1
}

SSH_CMD=(ssh -p "${SSH_PORT}")
if [[ -n "${IDENTITY_FILE}" ]]; then
  [[ -f "${IDENTITY_FILE}" ]] || {
    echo "SSH identity file not found: ${IDENTITY_FILE}" >&2
    exit 1
  }
  SSH_CMD+=(-i "${IDENTITY_FILE}")
fi
if [[ -n "${SSH_OPTS}" ]]; then
  # shellcheck disable=SC2206
  EXTRA_SSH_OPTS=(${SSH_OPTS})
  SSH_CMD+=("${EXTRA_SSH_OPTS[@]}")
fi

remote_script="set -euo pipefail; mkdir -p $(shell_quote "${TARGET_PATH}")"
if [[ "${WIPE_TARGET}" == "true" ]]; then
  remote_script="${remote_script}; find $(shell_quote "${TARGET_PATH}") -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +"
fi
remote_script="${remote_script}; tar -xzpf - -C $(shell_quote "${TARGET_PATH}") --strip-components ${STRIP_COMPONENTS}"

if [[ -n "${REMOTE_SUDO}" ]]; then
  remote_command="${REMOTE_SUDO} sh -ec $(shell_quote "${remote_script}")"
else
  remote_command="sh -ec $(shell_quote "${remote_script}")"
fi

echo "Restoring $(basename "${ARCHIVE}") to ${SSH_TARGET}:${TARGET_PATH}"
"${SSH_CMD[@]}" "${SSH_TARGET}" "${remote_command}" < "${ARCHIVE}"
echo "Host-path restore finished: ${SSH_TARGET}:${TARGET_PATH}"
