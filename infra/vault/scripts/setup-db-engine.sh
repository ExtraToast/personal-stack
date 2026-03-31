#!/usr/bin/env bash
# setup-db-engine.sh — Configure the Vault database secrets engine for dynamic credentials.
# Designed to be sourced by init-vault.sh or run standalone with vault_exec() available.
#
# Prerequisites:
#   - Vault is unsealed and the database secrets engine is enabled
#   - VAULT_TOKEN, VAULT_CONTAINER are set
#   - PostgreSQL is running with a superuser that can CREATE ROLE / GRANT
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Allow sourcing: if vault_exec is already defined, skip redefinition
if ! declare -f vault_exec > /dev/null 2>&1; then
  VAULT_CONTAINER="${VAULT_CONTAINER:-$(docker ps --filter "name=personal-stack_vault" --format "{{.ID}}" | head -1)}"
  vault_exec() {
    docker exec \
      -e VAULT_ADDR=http://127.0.0.1:8200 \
      -e VAULT_TOKEN="${VAULT_TOKEN:-}" \
      "$VAULT_CONTAINER" vault "$@"
  }
fi

# Allow sourcing: if logging helpers are not defined, use echo
if ! declare -f ok > /dev/null 2>&1; then
  ok()   { echo "  + $*"; }
  info() { echo "  $*"; }
  warn() { echo "  ! $*"; }
  die()  { echo "  ERROR: $*" >&2; exit 1; }
fi

setup_database_engine() {
  local pg_host="${PG_HOST:-postgres}"
  local pg_port="${PG_PORT:-5432}"
  local pg_superuser="${POSTGRES_USER:-postgres}"
  local pg_superpass="${POSTGRES_PASSWORD:-}"

  if [[ -z "$pg_superpass" ]]; then
    die "POSTGRES_PASSWORD must be set to configure the database secrets engine"
  fi

  info "Configuring database connection..."
  vault_exec write database/config/postgres \
    plugin_name=postgresql-database-plugin \
    "connection_url=postgresql://{{username}}:{{password}}@${pg_host}:${pg_port}/postgres?sslmode=disable" \
    allowed_roles="auth-api,assistant-api" \
    "username=${pg_superuser}" \
    "password=${pg_superpass}" > /dev/null
  ok "database/config/postgres"

  # Rotate the root password so the original superuser password is no longer stored in Vault
  # config. Vault generates a new one and only Vault knows it.
  vault_exec write -force database/rotate-root/postgres > /dev/null 2>&1 || true
  ok "Root credentials rotated (only Vault knows the new password)"

  info "Creating dynamic credential roles..."

  # auth-api: full access to auth_db
  vault_exec write database/roles/auth-api \
    db_name=postgres \
    creation_statements="
      CREATE ROLE \"{{name}}\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}';
      GRANT CONNECT ON DATABASE auth_db TO \"{{name}}\";
      GRANT USAGE ON SCHEMA public TO \"{{name}}\";
      GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO \"{{name}}\";
      GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO \"{{name}}\";
      ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO \"{{name}}\";
      ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON SEQUENCES TO \"{{name}}\";" \
    revocation_statements="
      REASSIGN OWNED BY \"{{name}}\" TO auth_user;
      DROP OWNED BY \"{{name}}\";
      DROP ROLE IF EXISTS \"{{name}}\";" \
    default_ttl=1h \
    max_ttl=24h > /dev/null
  ok "database/roles/auth-api (1h TTL, 24h max)"

  # assistant-api: full access to assistant_db
  vault_exec write database/roles/assistant-api \
    db_name=postgres \
    creation_statements="
      CREATE ROLE \"{{name}}\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}';
      GRANT CONNECT ON DATABASE assistant_db TO \"{{name}}\";
      GRANT USAGE ON SCHEMA public TO \"{{name}}\";
      GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO \"{{name}}\";
      GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO \"{{name}}\";
      ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO \"{{name}}\";
      ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON SEQUENCES TO \"{{name}}\";" \
    revocation_statements="
      REASSIGN OWNED BY \"{{name}}\" TO assistant_user;
      DROP OWNED BY \"{{name}}\";
      DROP ROLE IF EXISTS \"{{name}}\";" \
    default_ttl=1h \
    max_ttl=24h > /dev/null
  ok "database/roles/assistant-api (1h TTL, 24h max)"
}

# Run if executed directly (not sourced)
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  setup_database_engine
fi
