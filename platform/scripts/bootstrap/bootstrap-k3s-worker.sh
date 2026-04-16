#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/../lib/host-env.sh"

require_single_node_arg "$(basename "$0")" "$@"

load_host_env "$1"
require_host_ssh

if [[ "${IS_WORKER}" != "true" ]]; then
  echo "Node ${NODE_NAME} is not marked as a k3s worker in the inventory" >&2
  exit 1
fi

if [[ "${IS_CONTROL_PLANE}" == "true" ]]; then
  echo "Node ${NODE_NAME} is itself the bootstrap control plane; no worker token copy is required" >&2
  exit 1
fi

worker_node_name="${NODE_NAME}"
worker_ssh_host="${SSH_HOST}"
worker_ssh_user="${SSH_USER}"
worker_ssh_port="${SSH_PORT}"
bootstrap_control_plane_node="${K3S_BOOTSTRAP_CONTROL_PLANE_NODE}"
worker_join_token_file="${K3S_WORKER_JOIN_TOKEN_FILE}"
control_plane_token_file="${K3S_CONTROL_PLANE_TOKEN_FILE}"
worker_join_token_dir="$(dirname "${worker_join_token_file}")"

load_host_env "${bootstrap_control_plane_node}"
require_host_ssh

control_plane_ssh_host="${SSH_HOST}"
control_plane_ssh_user="${SSH_USER}"
control_plane_ssh_port="${SSH_PORT}"

worker_ssh=(
  ssh
  -p
  "${worker_ssh_port}"
  "${worker_ssh_user}@${worker_ssh_host}"
)
control_plane_ssh=(
  ssh
  -p
  "${control_plane_ssh_port}"
  "${control_plane_ssh_user}@${control_plane_ssh_host}"
)

control_plane_token="$("${control_plane_ssh[@]}" "sudo cat '${control_plane_token_file}'")"
if [[ -z "${control_plane_token}" ]]; then
  echo "Bootstrap control plane ${bootstrap_control_plane_node} returned an empty k3s token" >&2
  exit 1
fi

"${worker_ssh[@]}" "sudo install -d -m 0700 -o root -g root '${worker_join_token_dir}'"
printf '%s\n' "${control_plane_token}" |
  "${worker_ssh[@]}" "sudo tee '${worker_join_token_file}' >/dev/null && sudo chown root:root '${worker_join_token_file}' && sudo chmod 600 '${worker_join_token_file}'"

echo "Copied the k3s worker join token from ${bootstrap_control_plane_node} to ${worker_node_name}:${worker_join_token_file}"
echo "Next run: platform/scripts/deploy/deploy-host.sh ${worker_node_name}"
