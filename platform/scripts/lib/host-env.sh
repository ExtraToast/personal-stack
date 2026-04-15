#!/usr/bin/env bash
set -euo pipefail

platform_repo_root() {
  local script_dir
  script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  cd "${script_dir}/../../.." && pwd
}

platform_flake_dir() {
  printf '%s/platform\n' "$(platform_repo_root)"
}

platform_gradlew() {
  printf '%s\n' "${PLATFORM_GRADLEW:-$(platform_repo_root)/gradlew}"
}

platform_nix() {
  printf '%s\n' "${PLATFORM_NIX:-nix}"
}

platform_authorized_keys_file() {
  printf '%s\n' "${PLATFORM_AUTHORIZED_KEYS_FILE:-$(platform_flake_dir)/nix/authorized-keys.nix}"
}

require_single_node_arg() {
  local script_name="$1"
  shift

  if [[ "$#" -ne 1 ]]; then
    echo "Usage: ${script_name} <node-name>" >&2
    exit 1
  fi
}

load_host_env() {
  load_platform_env_with_command "show-host-env" "$1"
}

load_install_host_env() {
  load_platform_env_with_command "show-install-host-env" "$1"
}

load_platform_env_with_command() {
  local command_name="$1"
  local node_name="$2"
  local gradlew
  local env_output

  gradlew="$(platform_gradlew)"
  env_output="$("${gradlew}" -q :platform:tooling:run --args="${command_name} ${node_name}")"

  while IFS='=' read -r key value; do
    if [[ -z "${key}" ]]; then
      continue
    fi

    printf -v "${key}" '%s' "${value}"
    export "${key}"
  done <<< "${env_output}"
}

require_host_ssh() {
  if [[ "${HAS_SSH:-false}" != "true" ]]; then
    echo "Node ${NODE_NAME:-unknown} does not define SSH connection details" >&2
    exit 1
  fi
}

require_bootstrap_ssh() {
  if [[ "${HAS_SSH:-false}" != "true" ]]; then
    echo "Node ${NODE_NAME:-unknown} does not define bootstrap SSH connection details" >&2
    exit 1
  fi
}

require_authorized_keys_file() {
  local authorized_keys_file
  authorized_keys_file="$(platform_authorized_keys_file)"

  if [[ ! -f "${authorized_keys_file}" ]]; then
    echo "Missing ${authorized_keys_file}; create it from platform/nix/authorized-keys.nix.example before installing a key-only host" >&2
    exit 1
  fi
}
