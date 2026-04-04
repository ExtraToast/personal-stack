#!/usr/bin/env bash
# Dump Nomad, systemd, and allocation diagnostics for CI failures.

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
# shellcheck source=.github/scripts/nomad-ci-lib.sh
source "${SCRIPT_DIR}/nomad-ci-lib.sh"

export STACK_DIR="${STACK_DIR:-${GITHUB_WORKSPACE:-${ROOT_DIR}}}"
export BOOTSTRAP_ENV_FILE="${STACK_DIR}/.nomad-bootstrap.env"
export VAULT_KEYS_FILE="${STACK_DIR}/.vault-keys"
export NOMAD_KEYS_FILE="${STACK_DIR}/.nomad-keys"
export VAULT_ADDR="${VAULT_ADDR:-http://127.0.0.1:8200}"
export NOMAD_ADDR="${NOMAD_ADDR:-http://127.0.0.1:4646}"

dump_ci_diagnostics
