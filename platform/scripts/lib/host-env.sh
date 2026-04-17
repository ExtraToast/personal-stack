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

platform_flake_ref() {
  printf 'path:%s\n' "$(platform_flake_dir)"
}

platform_gradlew() {
  printf '%s\n' "${PLATFORM_GRADLEW:-$(platform_repo_root)/gradlew}"
}

platform_nix() {
  printf '%s\n' "${PLATFORM_NIX:-nix}"
}

platform_nix_experimental_features() {
  printf '%s\n' "${PLATFORM_NIX_EXPERIMENTAL_FEATURES:-nix-command flakes}"
}

platform_nix_config() {
  local features existing
  features="$(platform_nix_experimental_features)"
  existing="${NIX_CONFIG:-}"

  if [[ -n "${features}" ]]; then
    if [[ -n "${existing}" ]]; then
      printf '%s\nexperimental-features = %s\n' "${existing}" "${features}"
    else
      printf 'experimental-features = %s\n' "${features}"
    fi
  else
    printf '%s\n' "${existing}"
  fi
}

platform_current_system() {
  if [[ -n "${PLATFORM_CURRENT_SYSTEM:-}" ]]; then
    printf '%s\n' "${PLATFORM_CURRENT_SYSTEM}"
    return 0
  fi

  local machine kernel nix_machine nix_kernel
  machine="$(uname -m)"
  kernel="$(uname -s)"

  case "${machine}" in
    x86_64|amd64)
      nix_machine="x86_64"
      ;;
    arm64|aarch64)
      nix_machine="aarch64"
      ;;
    *)
      echo "Unsupported local machine architecture: ${machine}" >&2
      return 1
      ;;
  esac

  case "${kernel}" in
    Linux)
      nix_kernel="linux"
      ;;
    Darwin)
      nix_kernel="darwin"
      ;;
    *)
      echo "Unsupported local kernel: ${kernel}" >&2
      return 1
      ;;
  esac

  printf '%s-%s\n' "${nix_machine}" "${nix_kernel}"
}

platform_install_build_on() {
  printf '%s\n' "${PLATFORM_INSTALL_BUILD_ON:-auto}"
}

platform_deploy_build_on() {
  printf '%s\n' "${PLATFORM_DEPLOY_BUILD_ON:-auto}"
}

run_platform_nix() {
  local nix_bin features nix_config
  nix_bin="$(platform_nix)"
  features="$(platform_nix_experimental_features)"
  nix_config="$(platform_nix_config)"

  if [[ -n "${features}" ]]; then
    NIX_CONFIG="${nix_config}" "${nix_bin}" --extra-experimental-features "${features}" "$@"
  else
    if [[ -n "${nix_config}" ]]; then
      NIX_CONFIG="${nix_config}" "${nix_bin}" "$@"
    else
      "${nix_bin}" "$@"
    fi
  fi
}

platform_install_ssh_host_override() {
  printf '%s\n' "${PLATFORM_INSTALL_SSH_HOST:-}"
}

platform_install_ssh_user_override() {
  printf '%s\n' "${PLATFORM_INSTALL_SSH_USER:-}"
}

platform_install_ssh_port_override() {
  printf '%s\n' "${PLATFORM_INSTALL_SSH_PORT:-}"
}

platform_ssh_identity_file() {
  printf '%s\n' "${PLATFORM_SSH_IDENTITY_FILE:-}"
}

platform_authorized_keys_dir() {
  printf '%s\n' "${PLATFORM_AUTHORIZED_KEYS_DIR:-$(platform_flake_dir)/nix/authorized-keys}"
}

platform_deploy_authorized_keys_file() {
  printf '%s/deploy.pub\n' "$(platform_authorized_keys_dir)"
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
  if [[ "${HAS_BOOTSTRAP_SSH:-false}" != "true" ]]; then
    echo "Node ${NODE_NAME:-unknown} does not define bootstrap SSH connection details" >&2
    exit 1
  fi
}

require_deploy_authorized_keys_file() {
  local authorized_keys_file
  authorized_keys_file="$(platform_deploy_authorized_keys_file)"

  if [[ ! -f "${authorized_keys_file}" ]]; then
    echo "Missing deploy SSH public key file: ${authorized_keys_file}" >&2
    echo "Create platform/nix/authorized-keys/deploy.pub with at least one SSH public key before installing or deploying." >&2
    exit 1
  fi

  if ! grep -q '^[^#[:space:]]' "${authorized_keys_file}"; then
    echo "Deploy SSH public key file is empty: ${authorized_keys_file}" >&2
    echo "Add at least one SSH public key line to platform/nix/authorized-keys/deploy.pub." >&2
    exit 1
  fi
}

require_platform_ssh_identity_file_if_set() {
  local identity_file
  identity_file="$(platform_ssh_identity_file)"

  if [[ -n "${identity_file}" && ! -f "${identity_file}" ]]; then
    echo "SSH identity file not found: ${identity_file}" >&2
    exit 1
  fi
}

apply_install_ssh_overrides() {
  local override_host override_user override_port
  override_host="$(platform_install_ssh_host_override)"
  override_user="$(platform_install_ssh_user_override)"
  override_port="$(platform_install_ssh_port_override)"

  if [[ -z "${override_host}" && -z "${override_user}" && -z "${override_port}" ]]; then
    return 0
  fi

  if [[ -z "${override_host}" || -z "${override_user}" ]]; then
    echo "PLATFORM_INSTALL_SSH_HOST and PLATFORM_INSTALL_SSH_USER must be set together when overriding install SSH" >&2
    exit 1
  fi

  SSH_HOST="${override_host}"
  SSH_USER="${override_user}"
  SSH_PORT="${override_port:-22}"
  HAS_SSH=true
}

install_should_build_on_remote() {
  local build_on current_system
  build_on="$(platform_install_build_on)"

  case "${build_on}" in
    remote)
      return 0
      ;;
    local)
      return 1
      ;;
    auto)
      current_system="$(platform_current_system)"
      [[ "${current_system}" != "${NIX_SYSTEM:-}" ]]
      return
      ;;
    *)
      echo "Unsupported PLATFORM_INSTALL_BUILD_ON setting: ${build_on}" >&2
      exit 1
      ;;
  esac
}

deploy_should_build_on_remote() {
  local build_on current_system
  build_on="$(platform_deploy_build_on)"

  case "${build_on}" in
    remote)
      return 0
      ;;
    local)
      return 1
      ;;
    auto)
      current_system="$(platform_current_system)"
      [[ "${current_system}" != "${NIX_SYSTEM:-}" ]]
      return
      ;;
    *)
      echo "Unsupported PLATFORM_DEPLOY_BUILD_ON setting: ${build_on}" >&2
      exit 1
      ;;
  esac
}
