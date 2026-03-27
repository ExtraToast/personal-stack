#!/usr/bin/env bash
# init-vault.sh — first-time Vault setup for personal-stack.
# Run after the stack is deployed with placeholder secrets.
#
# What it does (in order):
#   1. Waits for the Vault container and API
#   2. Initializes Vault (1 key share, threshold 1)
#   3. Unseals Vault
#   4. Enables secrets engines (kv, database, transit, pki)
#   5. Creates transit encryption keys
#   6. Applies ACL policies
#   7. Enables AppRole auth + creates roles
#   8. Populates Vault KV with application secrets
#   9. Tears down stack, swaps placeholder secrets, redeploys
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
POLICIES_DIR="${SCRIPT_DIR}/../policies"
STACK_DIR="/opt/personal-stack"
VAULT_KEYS_FILE="${STACK_DIR}/.vault-keys"
VAULT_APP_SECRETS_FILE="${STACK_DIR}/.vault-app-secrets"
LOG_FILE="${STACK_DIR}/init-vault.log"

# ── Formatting helpers ──────────────────────────────────────────────────────
BOLD='\033[1m'
DIM='\033[2m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
RESET='\033[0m'

STEP=0
TOTAL_STEPS=9

step() {
  STEP=$((STEP + 1))
  printf "\n${BOLD}${CYAN}[%d/%d]${RESET} ${BOLD}%s${RESET}\n" "$STEP" "$TOTAL_STEPS" "$*"
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] [${STEP}/${TOTAL_STEPS}] $*" >> "$LOG_FILE"
}

ok()   { printf "  ${GREEN}+${RESET} %s\n" "$*"; echo "[$(date '+%Y-%m-%d %H:%M:%S')]   OK: $*" >> "$LOG_FILE"; }
info() { printf "  ${DIM}%s${RESET}\n" "$*"; echo "[$(date '+%Y-%m-%d %H:%M:%S')]   $*" >> "$LOG_FILE"; }
warn() { printf "  ${YELLOW}!${RESET} %s\n" "$*"; echo "[$(date '+%Y-%m-%d %H:%M:%S')]   WARN: $*" >> "$LOG_FILE"; }

die() {
  printf "\n  ${RED}ERROR:${RESET} %s\n" "$*"
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] ERROR: $*" >> "$LOG_FILE"
  exit 1
}

clear_line() { printf "\r\033[K"; }
mask() { printf '%s****%s' "${1:0:3}" "${1: -2}"; }

trap 'echo "[$(date "+%Y-%m-%d %H:%M:%S")] ERR: init-vault.sh failed at line ${LINENO}" >> "$LOG_FILE"' ERR

# ── Banner ──────────────────────────────────────────────────────────────────
printf "\n${BOLD}"
printf "  +-----------------------------------------+\n"
printf "  |       personal-stack vault init          |\n"
printf "  +-----------------------------------------+${RESET}\n\n"

echo "[$(date '+%Y-%m-%d %H:%M:%S')] init-vault.sh started" >> "$LOG_FILE"

# ── 1. Find Vault container ────────────────────────────────────────────────
step "Waiting for Vault container"

elapsed=0
until VAULT_CONTAINER=$(docker ps --filter "name=personal-stack_vault" --format "{{.ID}}" | head -1) \
    && [ -n "$VAULT_CONTAINER" ]; do
  printf "\r  ${DIM}Searching for container [%ds]${RESET}" "$elapsed"
  sleep 3
  elapsed=$((elapsed + 3))
done
clear_line
ok "Container: ${VAULT_CONTAINER:0:12}"

vault_exec() {
  docker exec \
    -e VAULT_ADDR=http://127.0.0.1:8200 \
    -e VAULT_TOKEN="${VAULT_TOKEN:-}" \
    "$VAULT_CONTAINER" \
    vault "$@"
}

info "Waiting for Vault API..."
elapsed=0
until docker exec "$VAULT_CONTAINER" \
    wget -qO- http://127.0.0.1:8200/v1/sys/seal-status > /dev/null 2>&1; do
  printf "\r  ${DIM}Connecting to API [%ds]${RESET}" "$elapsed"
  sleep 3
  elapsed=$((elapsed + 3))
done
clear_line
ok "Vault API reachable"

# ── 2. Initialize Vault ────────────────────────────────────────────────────
step "Initializing Vault"

INIT_STATUS=$(docker exec "$VAULT_CONTAINER" \
    wget -qO- http://127.0.0.1:8200/v1/sys/init 2>/dev/null || echo '{}')

if echo "$INIT_STATUS" | grep -q '"initialized":false'; then
  info "Initializing with 1 key share, threshold 1..."

  if ! INIT_RESPONSE=$(vault_exec operator init -key-shares=1 -key-threshold=1 -format=json 2>&1); then
    echo "$INIT_RESPONSE" >> "$LOG_FILE"
    die "vault operator init failed (see log)"
  fi

  if command -v jq > /dev/null 2>&1; then
    UNSEAL_KEY=$(echo "$INIT_RESPONSE" | jq -r '.unseal_keys_b64[0]')
    ROOT_TOKEN=$(echo "$INIT_RESPONSE" | jq -r '.root_token')
  elif command -v python3 > /dev/null 2>&1; then
    UNSEAL_KEY=$(echo "$INIT_RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['unseal_keys_b64'][0])")
    ROOT_TOKEN=$(echo "$INIT_RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['root_token'])")
  else
    UNSEAL_KEY=$(echo "$INIT_RESPONSE" | grep -o '"unseal_keys_b64":\["[^"]*"' | grep -o '"[^"]*"$' | tr -d '"')
    ROOT_TOKEN=$(echo "$INIT_RESPONSE" | grep -o '"root_token":"[^"]*"' | cut -d'"' -f4)
  fi

  if [ -z "$UNSEAL_KEY" ] || [ -z "$ROOT_TOKEN" ]; then
    echo "$INIT_RESPONSE" >> "$LOG_FILE"
    die "Failed to parse unseal key or root token (see log)"
  fi

  mkdir -p "$(dirname "$VAULT_KEYS_FILE")"
  cat > "$VAULT_KEYS_FILE" <<EOF
VAULT_UNSEAL_KEY=${UNSEAL_KEY}
VAULT_ROOT_TOKEN=${ROOT_TOKEN}
EOF
  chmod 600 "$VAULT_KEYS_FILE"
  ok "Vault initialized"
  ok "Keys saved to ${VAULT_KEYS_FILE}"
else
  ok "Vault already initialized"
fi

# ── Load keys ──────────────────────────────────────────────────────────────
[[ -f "$VAULT_KEYS_FILE" ]] || die "Keys file not found at ${VAULT_KEYS_FILE}"
# shellcheck source=/dev/null
source "$VAULT_KEYS_FILE"

VAULT_TOKEN="$VAULT_ROOT_TOKEN"
export VAULT_TOKEN

# ── 3. Unseal Vault ────────────────────────────────────────────────────────
step "Unsealing Vault"

SEAL_STATUS=$(vault_exec status -format=json 2>/dev/null || echo '{"sealed":true}')
if echo "$SEAL_STATUS" | grep -q '"sealed":true'; then
  vault_exec operator unseal "$VAULT_UNSEAL_KEY" > /dev/null
  ok "Vault unsealed"
else
  ok "Vault already unsealed"
fi

info "Waiting for active status..."
elapsed=0
until vault_exec status > /dev/null 2>&1; do
  printf "\r  ${DIM}Waiting [%ds]${RESET}" "$elapsed"
  sleep 2
  elapsed=$((elapsed + 2))
done
clear_line
ok "Vault active"

# ── 4. Enable secrets engines ──────────────────────────────────────────────
step "Enabling secrets engines"

for engine in "secret|-path=secret -version=2|kv" "database||database" "transit||transit" "pki||pki"; do
  IFS='|' read -r name flags type <<< "$engine"
  # shellcheck disable=SC2086
  vault_exec secrets enable $flags $type 2>/dev/null || true
  ok "$name"
done

# ── 5. Create transit keys ─────────────────────────────────────────────────
step "Creating transit encryption keys"

for key in auth-api assistant-api; do
  vault_exec write -f "transit/keys/${key}" > /dev/null 2>&1 || true
  ok "$key"
done

# ── 6. Apply policies ──────────────────────────────────────────────────────
step "Applying ACL policies"

for policy in admin auth-api assistant-api; do
  pfile="${POLICIES_DIR}/${policy}.hcl"
  [[ -f "$pfile" ]] || die "Policy file not found: $pfile"
  docker exec -i \
    -e VAULT_ADDR=http://127.0.0.1:8200 \
    -e VAULT_TOKEN="$VAULT_TOKEN" \
    "$VAULT_CONTAINER" \
    vault policy write "$policy" - < "$pfile" > /dev/null
  ok "$policy"
done

# ── 7. Enable AppRole auth + create roles ──────────────────────────────────
step "Configuring AppRole authentication"

vault_exec auth enable approle 2>/dev/null || true
ok "AppRole auth method enabled"

for role in auth-api assistant-api; do
  vault_exec write "auth/approle/role/${role}" \
    token_policies="${role}" \
    token_ttl=1h \
    token_max_ttl=4h \
    secret_id_ttl=0 > /dev/null
  ok "Role: ${role}"
done

AUTH_API_ROLE_ID=$(vault_exec read -field=role_id auth/approle/role/auth-api/role-id)
AUTH_API_SECRET_ID=$(vault_exec write -f -field=secret_id auth/approle/role/auth-api/secret-id)
ASSISTANT_API_ROLE_ID=$(vault_exec read -field=role_id auth/approle/role/assistant-api/role-id)
ASSISTANT_API_SECRET_ID=$(vault_exec write -f -field=secret_id auth/approle/role/assistant-api/secret-id)

ok "auth-api      role_id: $(mask "$AUTH_API_ROLE_ID")"
ok "assistant-api role_id: $(mask "$ASSISTANT_API_ROLE_ID")"

# ── 8. Populate Vault KV ──────────────────────────────────────────────────
step "Populating Vault KV with application secrets"

if [[ -f "$VAULT_APP_SECRETS_FILE" ]]; then
  # shellcheck source=/dev/null
  source "$VAULT_APP_SECRETS_FILE"

  : "${AUTH_DB_USER:=auth_user}"
  : "${AUTH_DB_PASSWORD:=auth_password}"
  : "${ASSISTANT_DB_USER:=assistant_user}"
  : "${ASSISTANT_DB_PASSWORD:=assistant_password}"

  vault_exec kv put secret/auth-api \
    "spring.rabbitmq.username=${RABBITMQ_USER}" \
    "spring.rabbitmq.password=${RABBITMQ_PASSWORD}" \
    "spring.datasource.username=${AUTH_DB_USER}" \
    "spring.datasource.password=${AUTH_DB_PASSWORD}" > /dev/null
  ok "secret/auth-api"

  vault_exec kv put secret/assistant-api \
    "spring.rabbitmq.username=${RABBITMQ_USER}" \
    "spring.rabbitmq.password=${RABBITMQ_PASSWORD}" \
    "spring.datasource.username=${ASSISTANT_DB_USER}" \
    "spring.datasource.password=${ASSISTANT_DB_PASSWORD}" > /dev/null
  ok "secret/assistant-api"

  info "Keys: spring.rabbitmq.*, spring.datasource.*"
else
  warn "${VAULT_APP_SECRETS_FILE} not found -- skipping KV population"
  warn "RabbitMQ and DB credentials must be written to Vault manually"
fi

# ── 9. Swap placeholder secrets + redeploy ─────────────────────────────────
step "Updating Docker Swarm secrets and redeploying"

info "Removing stack to release placeholder secrets..."
docker stack rm personal-stack > /dev/null 2>&1

elapsed=0
while [ "$elapsed" -lt 60 ]; do
  remaining=$(docker service ls --filter name=personal-stack_ -q 2>/dev/null | wc -l)
  [ "$remaining" -eq 0 ] && break
  printf "\r  ${DIM}Draining services [%ds]${RESET}" "$elapsed"
  sleep 2
  elapsed=$((elapsed + 2))
done
clear_line

elapsed=0
while [ "$elapsed" -lt 60 ]; do
  if ! docker network inspect personal-stack_personal-stack-overlay > /dev/null 2>&1; then
    break
  fi
  printf "\r  ${DIM}Waiting for network cleanup [%ds]${RESET}" "$elapsed"
  sleep 2
  elapsed=$((elapsed + 2))
done
clear_line
ok "Stack removed"

info "Replacing Vault secrets..."
update_secret() {
  local name="$1" value="$2"
  docker secret rm "$name" 2>/dev/null || true
  printf '%s' "$value" | docker secret create "$name" - > /dev/null
}
update_secret vault_auth_api_role_id      "$AUTH_API_ROLE_ID"
update_secret vault_auth_api_secret_id    "$AUTH_API_SECRET_ID"
update_secret vault_assistant_api_role_id "$ASSISTANT_API_ROLE_ID"
update_secret vault_assistant_api_secret_id "$ASSISTANT_API_SECRET_ID"
ok "Docker Swarm secrets updated"

info "Redeploying stack..."
docker stack deploy \
  -c "${STACK_DIR}/docker-compose.prod.yml" \
  personal-stack \
  --with-registry-auth > /dev/null 2>&1
ok "Stack deployed"

# Wait for Vault to come back and unseal it
info "Waiting for Vault to restart..."
elapsed=0
while [ "$elapsed" -lt 90 ]; do
  VAULT_CONTAINER=$(docker ps --filter "name=personal-stack_vault" --format "{{.ID}}" | head -1)
  if [ -n "$VAULT_CONTAINER" ]; then
    if docker exec "$VAULT_CONTAINER" wget -qO- http://127.0.0.1:8200/v1/sys/seal-status > /dev/null 2>&1; then
      break
    fi
  fi
  printf "\r  ${DIM}Waiting for Vault API [%ds]${RESET}" "$elapsed"
  sleep 3
  elapsed=$((elapsed + 3))
done
clear_line

if [ -n "$VAULT_CONTAINER" ]; then
  vault_exec operator unseal "$VAULT_UNSEAL_KEY" > /dev/null 2>&1 || true
  ok "Vault unsealed after redeploy"
else
  warn "Vault container not found -- unseal manually"
fi

# ── Complete ────────────────────────────────────────────────────────────────
printf "\n${BOLD}${GREEN}  +-----------------------------------------+${RESET}\n"
printf "${BOLD}${GREEN}  |      Vault initialization complete       |${RESET}\n"
printf "${BOLD}${GREEN}  +-----------------------------------------+${RESET}\n"
printf "  ${DIM}auth-api      role_id: $(mask "$AUTH_API_ROLE_ID")${RESET}\n"
printf "  ${DIM}assistant-api role_id: $(mask "$ASSISTANT_API_ROLE_ID")${RESET}\n"
printf "  ${DIM}Full log: ${LOG_FILE}${RESET}\n\n"

echo "[$(date '+%Y-%m-%d %H:%M:%S')] init-vault.sh complete" >> "$LOG_FILE"
