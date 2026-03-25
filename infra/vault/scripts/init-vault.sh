#!/usr/bin/env bash
set -euo pipefail

VAULT_ADDR="${VAULT_ADDR:-http://127.0.0.1:8200}"
export VAULT_ADDR

echo "==> Waiting for Vault to be ready..."
until vault status >/dev/null 2>&1; do
  sleep 2
done
echo "==> Vault is ready."

# ── Enable secrets engines ────────────────────────────────────────
echo "==> Enabling KV v2 secrets engine at secret/..."
vault secrets enable -path=secret -version=2 kv || true

echo "==> Enabling database secrets engine..."
vault secrets enable database || true

echo "==> Enabling transit secrets engine..."
vault secrets enable transit || true

echo "==> Enabling PKI secrets engine..."
vault secrets enable pki || true

# ── Create transit keys ──────────────────────────────────────────
echo "==> Creating transit encryption keys..."
vault write -f transit/keys/auth-api || true
vault write -f transit/keys/assistant-api || true

# ── Apply policies ───────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
POLICIES_DIR="${SCRIPT_DIR}/../policies"

echo "==> Writing policies..."
vault policy write admin "${POLICIES_DIR}/admin.hcl"
vault policy write auth-api "${POLICIES_DIR}/auth-api.hcl"
vault policy write assistant-api "${POLICIES_DIR}/assistant-api.hcl"

# ── Enable AppRole auth method ───────────────────────────────────
echo "==> Enabling AppRole auth method..."
vault auth enable approle || true

# ── Create AppRole for auth-api ──────────────────────────────────
echo "==> Creating AppRole for auth-api..."
vault write auth/approle/role/auth-api \
  token_policies="auth-api" \
  token_ttl=1h \
  token_max_ttl=4h \
  secret_id_ttl=0

AUTH_API_ROLE_ID=$(vault read -field=role_id auth/approle/role/auth-api/role-id)
AUTH_API_SECRET_ID=$(vault write -f -field=secret_id auth/approle/role/auth-api/secret-id)

# ── Create AppRole for assistant-api ─────────────────────────────
echo "==> Creating AppRole for assistant-api..."
vault write auth/approle/role/assistant-api \
  token_policies="assistant-api" \
  token_ttl=1h \
  token_max_ttl=4h \
  secret_id_ttl=0

ASSISTANT_API_ROLE_ID=$(vault read -field=role_id auth/approle/role/assistant-api/role-id)
ASSISTANT_API_SECRET_ID=$(vault write -f -field=secret_id auth/approle/role/assistant-api/secret-id)

# ── Print results ────────────────────────────────────────────────
echo ""
echo "============================================"
echo "  Vault initialization complete"
echo "============================================"
echo ""
echo "  auth-api:"
echo "    Role ID:   ${AUTH_API_ROLE_ID}"
echo "    Secret ID: ${AUTH_API_SECRET_ID}"
echo ""
echo "  assistant-api:"
echo "    Role ID:   ${ASSISTANT_API_ROLE_ID}"
echo "    Secret ID: ${ASSISTANT_API_SECRET_ID}"
echo ""
echo "  Store these values as Docker Swarm secrets:"
echo "    echo '${AUTH_API_ROLE_ID}' | docker secret create vault_auth_api_role_id -"
echo "    echo '${AUTH_API_SECRET_ID}' | docker secret create vault_auth_api_secret_id -"
echo "    echo '${ASSISTANT_API_ROLE_ID}' | docker secret create vault_assistant_api_role_id -"
echo "    echo '${ASSISTANT_API_SECRET_ID}' | docker secret create vault_assistant_api_secret_id -"
echo "============================================"
