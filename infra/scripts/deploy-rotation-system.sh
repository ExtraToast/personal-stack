#!/usr/bin/env bash
# deploy-rotation-system.sh — One-time setup for the credential rotation system.
#
# Run this on the server after git pull to:
#   1. Configure the Vault database secrets engine (dynamic DB credentials)
#   2. Run repair-server.sh (creates OAuth2 Swarm secrets, syncs Vault KV, redeploys)
#   3. Install the systemd timer for weekly rotation
#   4. Verify everything works with a dry run
#
# Prerequisites:
#   - Stack is running (docker stack ls shows personal-stack)
#   - Vault is initialized and unsealable (/.vault-keys exists)
#   - New code is pulled (git pull)
set -euo pipefail

STACK_DIR="/opt/personal-stack"
BOLD='\033[1m'
DIM='\033[2m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
RESET='\033[0m'

STEP=0
TOTAL=5

step() {
  STEP=$((STEP + 1))
  printf "\n${BOLD}${CYAN}[%d/%d]${RESET} ${BOLD}%s${RESET}\n" "$STEP" "$TOTAL" "$*"
}
ok()   { printf "  ${GREEN}+${RESET} %s\n" "$*"; }
info() { printf "  ${DIM}%s${RESET}\n" "$*"; }
die()  { printf "\n  ${RED}ERROR:${RESET} %s\n" "$*"; exit 1; }

printf "\n${BOLD}"
printf "  +-----------------------------------------+\n"
printf "  |   Deploy Credential Rotation System     |\n"
printf "  +-----------------------------------------+${RESET}\n\n"

# ── Preflight ─────────────────────────────────────────────────────────────
step "Preflight checks"

[[ -f "${STACK_DIR}/.vault-keys" ]] || die "Vault keys not found at ${STACK_DIR}/.vault-keys"
ok "Vault keys file exists"

[[ -f "${STACK_DIR}/infra/scripts/lib/common.sh" ]] || die "lib/common.sh not found — did you git pull?"
ok "Rotation scripts present"

VAULT_CONTAINER=$(docker ps --filter "name=personal-stack_vault" --format "{{.ID}}" | head -1)
[[ -n "$VAULT_CONTAINER" ]] || die "Vault container not running"
ok "Vault container: ${VAULT_CONTAINER:0:12}"

PG_CONTAINER=$(docker ps --filter "name=personal-stack_postgres" --format "{{.ID}}" | head -1)
[[ -n "$PG_CONTAINER" ]] || die "PostgreSQL container not running"
ok "PostgreSQL container: ${PG_CONTAINER:0:12}"

# Load Vault credentials
# shellcheck source=/dev/null
source "${STACK_DIR}/.vault-keys"
[[ -n "${VAULT_ROOT_TOKEN:-}" ]] || die "VAULT_ROOT_TOKEN not set"

vault_exec() {
  docker exec \
    -e VAULT_ADDR=http://127.0.0.1:8200 \
    -e VAULT_TOKEN="$VAULT_ROOT_TOKEN" \
    "$VAULT_CONTAINER" vault "$@"
}

# Unseal if needed
SEAL_STATUS=$(vault_exec status -format=json 2>/dev/null || echo '{"sealed":true}')
if echo "$SEAL_STATUS" | grep -q '"sealed":true'; then
  vault_exec operator unseal "$VAULT_UNSEAL_KEY" > /dev/null
  ok "Vault unsealed"
else
  ok "Vault already unsealed"
fi

# ── Step 1: Configure Vault database engine ───────────────────────────────
step "Configuring Vault database secrets engine"

PG_SUPERPASS=$(docker exec "$PG_CONTAINER" cat /run/secrets/postgres_password 2>/dev/null) \
  || die "Could not read postgres_password from container"
PG_SUPERUSER=$(docker exec "$PG_CONTAINER" cat /run/secrets/postgres_user 2>/dev/null) \
  || PG_SUPERUSER="postgres"

export VAULT_TOKEN="$VAULT_ROOT_TOKEN"
export VAULT_CONTAINER
export POSTGRES_PASSWORD="$PG_SUPERPASS"
export POSTGRES_USER="$PG_SUPERUSER"
export PG_HOST="postgres"
export PG_PORT="5432"

source "${STACK_DIR}/infra/vault/scripts/setup-db-engine.sh"
setup_database_engine
ok "Database secrets engine configured"

# Verify dynamic creds work
info "Testing dynamic credential generation..."
TEST_CREDS=$(vault_exec read -format=json database/creds/auth-api 2>&1) || true
if echo "$TEST_CREDS" | grep -q '"username"'; then
  ok "Dynamic DB credentials working"
  # Revoke the test lease immediately
  LEASE_ID=$(echo "$TEST_CREDS" | grep -o '"lease_id":"[^"]*"' | cut -d'"' -f4)
  vault_exec lease revoke "$LEASE_ID" > /dev/null 2>&1 || true
else
  die "Failed to generate dynamic DB credentials — check Vault logs"
fi

# ── Step 2: Run repair-server.sh ──────────────────────────────────────────
step "Running repair-server.sh (creates OAuth2 secrets, syncs Vault, redeploys)"
info "This will tear down and redeploy the stack — expect ~60s downtime"

bash "${STACK_DIR}/infra/repair-server.sh"
ok "Repair complete"

# ── Step 3: Install systemd timer ─────────────────────────────────────────
step "Installing systemd timer for weekly rotation"

cp "${STACK_DIR}/infra/systemd/credential-rotation.service" /etc/systemd/system/
cp "${STACK_DIR}/infra/systemd/credential-rotation.timer" /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now credential-rotation.timer
ok "Timer installed and enabled"

NEXT_RUN=$(systemctl list-timers credential-rotation.timer --no-pager | grep credential | awk '{print $1, $2, $3}')
info "Next scheduled run: ${NEXT_RUN:-check with 'systemctl list-timers'}"

# ── Step 4: Dry-run verification ──────────────────────────────────────────
step "Verifying rotation system (dry run)"

bash "${STACK_DIR}/infra/scripts/rotate-credentials.sh" --dry-run --tier 1 --force 2>&1 | head -30
ok "Dry run completed"

# ── Done ──────────────────────────────────────────────────────────────────
printf "\n${BOLD}${GREEN}  +-----------------------------------------+${RESET}\n"
printf "${BOLD}${GREEN}  |    Rotation system deployed              |${RESET}\n"
printf "${BOLD}${GREEN}  +-----------------------------------------+${RESET}\n\n"

printf "  ${BOLD}What's active now:${RESET}\n"
printf "  ${DIM}- Vault database engine: dynamic DB creds for auth-api, assistant-api${RESET}\n"
printf "  ${DIM}- OAuth2 client secrets: stored in Vault KV + Swarm secrets${RESET}\n"
printf "  ${DIM}- Systemd timer: weekly rotation every Sunday 03:00 UTC${RESET}\n"
printf "\n"
printf "  ${BOLD}Useful commands:${RESET}\n"
printf "  ${DIM}  systemctl list-timers credential-rotation.timer${RESET}\n"
printf "  ${DIM}  rotate-credentials.sh --dry-run --all${RESET}\n"
printf "  ${DIM}  rotate-credentials.sh --only approle --force${RESET}\n"
printf "  ${DIM}  journalctl -u credential-rotation.service${RESET}\n\n"
