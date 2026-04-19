#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESTORE_HOSTPATH_SCRIPT="${SCRIPT_DIR}/restore-hostpath-archive.sh"

usage() {
  cat <<'EOF' >&2
Usage: restore-media-hostpaths.sh --backup-dir <backups/run-YYYYMMDD> --ssh-target <user@host> [options]

Restores the media app host-path directories used by the current k3s manifests:
  - qbittorrent
  - prowlarr
  - bazarr
  - sonarr
  - radarr
  - jellyfin
  - jellyseerr

Options:
  --backup-dir <dir>      Backup run directory.
  --ssh-target <user@host>
  --ssh-port <port>       Default: 22.
  --identity-file <path>  SSH identity file.
  --ssh-opts "<opts>"     Extra SSH options, shell-split.
  --sudo "<cmd>"          Remote privilege wrapper. Default: sudo -n.
EOF
  exit 1
}

BACKUP_DIR=""
SSH_TARGET=""
SSH_PORT=22
IDENTITY_FILE=""
SSH_OPTS=""
REMOTE_SUDO="sudo -n"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --backup-dir)
      BACKUP_DIR="${2:?Missing value for --backup-dir}"
      shift 2
      ;;
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
[[ -n "${SSH_TARGET}" ]] || usage

restore_hostpath() {
  local archive="$1"
  local target_path="$2"
  local cmd=(
    "${RESTORE_HOSTPATH_SCRIPT}"
    --ssh-target "${SSH_TARGET}"
    --ssh-port "${SSH_PORT}"
    --target-path "${target_path}"
    --archive "${archive}"
    --strip-components 3
    --wipe-target
  )

  if [[ -n "${IDENTITY_FILE}" ]]; then
    cmd+=(--identity-file "${IDENTITY_FILE}")
  fi

  if [[ -n "${SSH_OPTS}" ]]; then
    cmd+=(--ssh-opts "${SSH_OPTS}")
  fi

  if [[ -n "${REMOTE_SUDO}" ]]; then
    cmd+=(--sudo "${REMOTE_SUDO}")
  fi

  "${cmd[@]}"
}

restore_hostpath "${BACKUP_DIR}/home/qbittorrent-config.tar.gz" /var/lib/personal-stack/media/qbittorrent
restore_hostpath "${BACKUP_DIR}/home/prowlarr-config.tar.gz" /var/lib/personal-stack/media/prowlarr
restore_hostpath "${BACKUP_DIR}/home/bazarr-config.tar.gz" /var/lib/personal-stack/media/bazarr
restore_hostpath "${BACKUP_DIR}/home/sonarr-config.tar.gz" /var/lib/personal-stack/media/sonarr
restore_hostpath "${BACKUP_DIR}/home/radarr-config.tar.gz" /var/lib/personal-stack/media/radarr
restore_hostpath "${BACKUP_DIR}/home/jellyfin-config.tar.gz" /var/lib/personal-stack/media/jellyfin
restore_hostpath "${BACKUP_DIR}/home/jellyseerr-config.tar.gz" /var/lib/personal-stack/media/jellyseerr

echo "Media host-path restores completed from ${BACKUP_DIR}"
