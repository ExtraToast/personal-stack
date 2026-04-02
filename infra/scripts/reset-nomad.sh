#!/usr/bin/env bash
# reset-nomad.sh — Stop all Nomad jobs and purge them so migrate.sh full
# can start clean. Does NOT touch Swarm, Vault keys, or bootstrap env.
set -euo pipefail

NOMAD_KEYS_FILE="${NOMAD_KEYS_FILE:-/opt/personal-stack/.nomad-keys}"
export NOMAD_ADDR="${NOMAD_ADDR:-http://127.0.0.1:4646}"

if [[ -f "${NOMAD_KEYS_FILE}" ]]; then
  source "${NOMAD_KEYS_FILE}"
  export NOMAD_TOKEN="${NOMAD_TOKEN:-${NOMAD_BOOTSTRAP_TOKEN:-}}"
fi

echo "Stopping and purging all Nomad jobs..."
for job in $(nomad job status -short 2>/dev/null | awk 'NR>1 {print $1}'); do
  echo "  stopping ${job}"
  nomad job stop -purge "${job}" 2>/dev/null || true
done

echo "Waiting for allocations to drain..."
sleep 5

echo "Remaining jobs:"
nomad job status -short 2>/dev/null || echo "  (none)"

echo ""
echo "Nomad is clean. You can now re-run:"
echo "  sudo bash infra/scripts/migrate.sh full"
