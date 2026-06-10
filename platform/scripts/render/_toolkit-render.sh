#!/usr/bin/env bash
# Shared helper: render a single generated artifact with the
# @extratoast/deploy-config-schema toolkit. The deploy-config is generated
# from platform/inventory/fleet.yaml (route rules are declared there).
#
# DEPLOY_CONFIG_SCHEMA_BIN overrides how the toolkit is invoked (CI installs
# a pinned copy and points this at its bin); locally it defaults to npx.

toolkit_cli="${DEPLOY_CONFIG_SCHEMA_BIN:-npx --yes @extratoast/deploy-config-schema@^0.7.0}"

render_adapter() {
  local adapter="$1"
  local output_path="$2"
  local repo_root="$3"
  local deploy_config
  deploy_config="$(mktemp)"
  # shellcheck disable=SC2086
  ${toolkit_cli} fleet-to-deploy-config "${repo_root}/platform/inventory/fleet.yaml" >"${deploy_config}"
  mkdir -p "$(dirname "${output_path}")"
  # shellcheck disable=SC2086
  ${toolkit_cli} render "${adapter}" "${deploy_config}" >"${output_path}"
  rm -f "${deploy_config}"
}
