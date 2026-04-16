#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Shared inventory resolution keeps shell logic thin and pushes YAML parsing into Kotlin.
source "${script_dir}/../lib/host-env.sh"

usage() {
  echo "Usage: $(basename "$0") [--ssh-key <identity-file> | --ssh-password <password>] <node-name>" >&2
  echo "Optional env overrides: PLATFORM_INSTALL_SSH_HOST PLATFORM_INSTALL_SSH_USER [PLATFORM_INSTALL_SSH_PORT]" >&2
  exit 1
}

INSTALL_SSH_KEY=""
INSTALL_SSH_PASSWORD=""
INSTALL_NODE_NAME=""

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --ssh-key)
      shift
      [[ "$#" -gt 0 ]] || usage
      INSTALL_SSH_KEY="$1"
      ;;
    --ssh-password)
      shift
      [[ "$#" -gt 0 ]] || usage
      INSTALL_SSH_PASSWORD="$1"
      ;;
    -h|--help)
      usage
      ;;
    -*)
      echo "Unknown option: $1" >&2
      usage
      ;;
    *)
      if [[ -n "${INSTALL_NODE_NAME}" ]]; then
        usage
      fi
      INSTALL_NODE_NAME="$1"
      ;;
  esac
  shift
done

[[ -n "${INSTALL_NODE_NAME}" ]] || usage

if [[ -n "${INSTALL_SSH_KEY}" && -n "${INSTALL_SSH_PASSWORD}" ]]; then
  echo "Choose either --ssh-key or --ssh-password, not both" >&2
  exit 1
fi

if [[ -n "${INSTALL_SSH_KEY}" && ! -f "${INSTALL_SSH_KEY}" ]]; then
  echo "SSH identity file not found: ${INSTALL_SSH_KEY}" >&2
  exit 1
fi

load_install_host_env "${INSTALL_NODE_NAME}"
apply_install_ssh_overrides
require_host_ssh
require_authorized_keys_file
require_install_ssh_key_in_authorized_keys "${INSTALL_SSH_KEY}"

cd "$(platform_flake_dir)"
flake_ref="$(platform_flake_ref)"
nix_args=(
  run
  "${flake_ref}#nixos-anywhere"
  --
  --flake
  "${flake_ref}#${NODE_NAME}"
  --target-host
  "${SSH_USER}@${SSH_HOST}"
  --ssh-port
  "${SSH_PORT}"
)

if install_should_build_on_remote; then
  nix_args+=(
    --build-on
    remote
  )
fi

if [[ -n "${INSTALL_SSH_KEY}" ]]; then
  nix_args+=(
    -i
    "${INSTALL_SSH_KEY}"
  )
fi

if [[ -n "${INSTALL_SSH_PASSWORD}" ]]; then
  export SSHPASS="${INSTALL_SSH_PASSWORD}"
  nix_args+=(--env-password)
fi

run_platform_nix "${nix_args[@]}"
