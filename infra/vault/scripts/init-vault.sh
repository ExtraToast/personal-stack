#!/usr/bin/env bash
set -euo pipefail

# Show exactly which line fails
trap 'echo "ERR: init-vault.sh failed at line ${LINENO}" >&2' ERR

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
POLICIES_DIR="${SCRIPT_DIR}/../policies"
VAULT_KEYS_FILE="/opt/personal-stack/.vault-keys"

# ── Find vault container (Swarm names it like "personal-stack_vault.1.<id>") ──
echo "==> Waiting for vault container to start..."
until VAULT_CONTAINER=$(docker ps --filter "name=personal-stack_vault" --format "{{.ID}}" | head -1) \
    && [ -n "$VAULT_CONTAINER" ]; do
  sleep 3
done
echo "==> Found vault container: $VAULT_CONTAINER"

# ── Helper: run vault CLI inside the container ────────────────────────────────
vault_exec() {
  docker exec \
    -e VAULT_ADDR=http://127.0.0.1:8200 \
    -e VAULT_TOKEN="${VAULT_TOKEN:-}" \
    "$VAULT_CONTAINER" \
    vault "$@"
}

# ── Wait for Vault API to be reachable inside the container ──────────────────
echo "==> Waiting for Vault to be reachable..."
until docker exec "$VAULT_CONTAINER" \
    wget -qO- http://127.0.0.1:8200/v1/sys/seal-status > /dev/null 2>&1; do
  sleep 3
done
echo "==> Vault is reachable."

# ── Initialize if needed ──────────────────────────────────────────────────────
INIT_STATUS=$(docker exec "$VAULT_CONTAINER" \
    wget -qO- http://127.0.0.1:8200/v1/sys/init 2>/dev/null || echo '{}')
if echo "$INIT_STATUS" | grep -q '"initialized":false'; then
  echo "==> Initializing Vault (1 key share, threshold 1 for simplicity)..."

  if ! INIT_RESPONSE=$(vault_exec operator init -key-shares=1 -key-threshold=1 -format=json 2>&1); then
    echo "ERR: vault operator init failed:"
    echo "$INIT_RESPONSE"
    exit 1
  fi

  echo "==> Parsing init response..."
  # Use jq if available, otherwise fall back to python3, then grep
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
    echo "ERR: Failed to parse unseal key or root token from init response:"
    echo "$INIT_RESPONSE"
    exit 1
  fi

  # Save keys to file (readable only by root)
  mkdir -p "$(dirname "$VAULT_KEYS_FILE")"
  cat > "$VAULT_KEYS_FILE" <<EOF
VAULT_UNSEAL_KEY=${UNSEAL_KEY}
VAULT_ROOT_TOKEN=${ROOT_TOKEN}
EOF
  chmod 600 "$VAULT_KEYS_FILE"
  echo "==> Vault keys saved to ${VAULT_KEYS_FILE}"
else
  echo "==> Vault already initialized."
fi

# ── Load keys ─────────────────────────────────────────────────────────────────
if [[ ! -f "$VAULT_KEYS_FILE" ]]; then
  echo "ERR: Vault keys file not found at ${VAULT_KEYS_FILE}"
  echo "     Vault was initialized outside this script. Cannot proceed."
  exit 1
fi
# shellcheck source=/dev/null
source "$VAULT_KEYS_FILE"

VAULT_TOKEN="$VAULT_ROOT_TOKEN"
export VAULT_TOKEN

# ── Unseal if sealed ──────────────────────────────────────────────────────────
SEAL_STATUS=$(vault_exec status -format=json 2>/dev/null || echo '{"sealed":true}')
if echo "$SEAL_STATUS" | grep -q '"sealed":true'; then
  echo "==> Unsealing Vault..."
  vault_exec operator unseal "$VAULT_UNSEAL_KEY" > /dev/null
  echo "==> Vault unsealed."
else
  echo "==> Vault already unsealed."
fi

# ── Wait for Vault to be fully ready (exit code 0 = unsealed + active) ───────
echo "==> Waiting for Vault to be active..."
until vault_exec status > /dev/null 2>&1; do
  sleep 2
done

# ── Enable secrets engines ────────────────────────────────────────────────────
echo "==> Enabling secrets engines..."
vault_exec secrets enable -path=secret -version=2 kv 2>/dev/null || true
vault_exec secrets enable database 2>/dev/null || true
vault_exec secrets enable transit 2>/dev/null || true
vault_exec secrets enable pki 2>/dev/null || true

# ── Create transit keys ───────────────────────────────────────────────────────
echo "==> Creating transit encryption keys..."
vault_exec write -f transit/keys/auth-api 2>/dev/null || true
vault_exec write -f transit/keys/assistant-api 2>/dev/null || true

# ── Apply policies (pipe from host via stdin) ─────────────────────────────────
echo "==> Writing policies..."
docker exec -i \
  -e VAULT_ADDR=http://127.0.0.1:8200 \
  -e VAULT_TOKEN="$VAULT_TOKEN" \
  "$VAULT_CONTAINER" \
  vault policy write admin - < "${POLICIES_DIR}/admin.hcl"

docker exec -i \
  -e VAULT_ADDR=http://127.0.0.1:8200 \
  -e VAULT_TOKEN="$VAULT_TOKEN" \
  "$VAULT_CONTAINER" \
  vault policy write auth-api - < "${POLICIES_DIR}/auth-api.hcl"

docker exec -i \
  -e VAULT_ADDR=http://127.0.0.1:8200 \
  -e VAULT_TOKEN="$VAULT_TOKEN" \
  "$VAULT_CONTAINER" \
  vault policy write assistant-api - < "${POLICIES_DIR}/assistant-api.hcl"

# ── Enable AppRole auth method ────────────────────────────────────────────────
echo "==> Enabling AppRole auth method..."
vault_exec auth enable approle 2>/dev/null || true

# ── Create AppRoles ───────────────────────────────────────────────────────────
echo "==> Creating AppRole for auth-api..."
vault_exec write auth/approle/role/auth-api \
  token_policies="auth-api" \
  token_ttl=1h \
  token_max_ttl=4h \
  secret_id_ttl=0

AUTH_API_ROLE_ID=$(vault_exec read -field=role_id auth/approle/role/auth-api/role-id)
AUTH_API_SECRET_ID=$(vault_exec write -f -field=secret_id auth/approle/role/auth-api/secret-id)

echo "==> Creating AppRole for assistant-api..."
vault_exec write auth/approle/role/assistant-api \
  token_policies="assistant-api" \
  token_ttl=1h \
  token_max_ttl=4h \
  secret_id_ttl=0

ASSISTANT_API_ROLE_ID=$(vault_exec read -field=role_id auth/approle/role/assistant-api/role-id)
ASSISTANT_API_SECRET_ID=$(vault_exec write -f -field=secret_id auth/approle/role/assistant-api/secret-id)

# ── Populate Vault KV with application secrets ────────────────────────────
VAULT_APP_SECRETS_FILE="/opt/personal-stack/.vault-app-secrets"
if [[ -f "$VAULT_APP_SECRETS_FILE" ]]; then
  echo "==> Populating Vault KV with application secrets..."
  # shellcheck source=/dev/null
  source "$VAULT_APP_SECRETS_FILE"

  # DB credential defaults (match init-databases.sh fallbacks)
  : "${AUTH_DB_USER:=auth_user}"
  : "${AUTH_DB_PASSWORD:=auth_password}"
  : "${ASSISTANT_DB_USER:=assistant_user}"
  : "${ASSISTANT_DB_PASSWORD:=assistant_password}"

  vault_exec kv put secret/auth-api \
    "spring.rabbitmq.username=${RABBITMQ_USER}" \
    "spring.rabbitmq.password=${RABBITMQ_PASSWORD}" \
    "spring.datasource.username=${AUTH_DB_USER}" \
    "spring.datasource.password=${AUTH_DB_PASSWORD}"
  vault_exec kv put secret/assistant-api \
    "spring.rabbitmq.username=${RABBITMQ_USER}" \
    "spring.rabbitmq.password=${RABBITMQ_PASSWORD}" \
    "spring.datasource.username=${ASSISTANT_DB_USER}" \
    "spring.datasource.password=${ASSISTANT_DB_PASSWORD}"
  echo "==> Vault KV secrets populated."
else
  echo "WARN: ${VAULT_APP_SECRETS_FILE} not found; skipping KV population."
  echo "     RabbitMQ and DB credentials must be written to Vault manually."
fi

# ── Update Docker Swarm secrets ───────────────────────────────────────────────
echo "==> Detaching Vault secrets from services (required before removal)..."
docker service update \
  --secret-rm vault_auth_api_role_id \
  --secret-rm vault_auth_api_secret_id \
  personal-stack_auth-api 2>/dev/null || true
docker service update \
  --secret-rm vault_assistant_api_role_id \
  --secret-rm vault_assistant_api_secret_id \
  personal-stack_assistant-api 2>/dev/null || true

echo "==> Updating Docker Swarm Vault secrets..."

update_secret() {
  local name="$1" value="$2"
  # Remove the placeholder secret and recreate with real value
  docker secret rm "$name" 2>/dev/null || true
  printf '%s' "$value" | docker secret create "$name" -
  echo "     $name updated"
}

update_secret vault_auth_api_role_id "$AUTH_API_ROLE_ID"
update_secret vault_auth_api_secret_id "$AUTH_API_SECRET_ID"
update_secret vault_assistant_api_role_id "$ASSISTANT_API_ROLE_ID"
update_secret vault_assistant_api_secret_id "$ASSISTANT_API_SECRET_ID"

# ── Done ──────────────────────────────────────────────────────────────────────
echo ""
echo "============================================"
echo "  Vault initialization complete"
echo "============================================"
echo "  auth-api      role_id: ${AUTH_API_ROLE_ID}"
echo "  assistant-api role_id: ${ASSISTANT_API_ROLE_ID}"
echo "  Docker Swarm secrets updated."
echo "============================================"
