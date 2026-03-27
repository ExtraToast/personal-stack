#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
POLICIES_DIR="${SCRIPT_DIR}/../policies"
VAULT_KEYS_FILE="/opt/private-stack/.vault-keys"

# ── Find vault container (Swarm names it like "private-stack_vault.1.<id>") ──
echo "==> Waiting for vault container to start..."
until VAULT_CONTAINER=$(docker ps --filter "name=private-stack_vault" --format "{{.ID}}" | head -1) \
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
  INIT_RESPONSE=$(vault_exec operator init -key-shares=1 -key-threshold=1 -format=json)

  UNSEAL_KEY=$(echo "$INIT_RESPONSE" | grep -oP '"unseal_keys_b64"\s*:\s*\[\s*"\K[^"]+')
  ROOT_TOKEN=$(echo "$INIT_RESPONSE" | grep -oP '"root_token"\s*:\s*"\K[^"]+')

  # Save keys to file (readable only by root)
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

# ── Wait for Vault to be fully ready ─────────────────────────────────────────
echo "==> Waiting for Vault to be active..."
until vault_exec status -format=json 2>/dev/null | grep -q '"initialized":true'; do
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

# ── Update Docker Swarm secrets ───────────────────────────────────────────────
echo "==> Updating Docker Swarm Vault secrets..."

update_secret() {
  local name="$1" value="$2"
  # Remove the placeholder secret and recreate with real value
  docker secret rm "$name" 2>/dev/null || true
  echo "$value" | docker secret create "$name" -
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
