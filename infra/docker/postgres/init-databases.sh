#!/usr/bin/env bash
set -euo pipefail

# This script is executed by the PostgreSQL entrypoint on first run.
# It creates separate databases and users for each service.
# In production, passwords are read from Docker secrets; defaults are for local dev.

read_secret() {
    local file="/run/secrets/$1"
    if [ -f "$file" ]; then
        cat "$file"
    else
        echo "$2"
    fi
}

resolve_value() {
    local env_name="$1"
    local secret_name="$2"
    local fallback="$3"
    local current="${!env_name:-}"

    if [ -n "$current" ]; then
        echo "$current"
    else
        read_secret "$secret_name" "$fallback"
    fi
}

AUTH_DB_PASSWORD=$(resolve_value AUTH_DB_PASSWORD auth_db_password auth_password)
ASSISTANT_DB_PASSWORD=$(resolve_value ASSISTANT_DB_PASSWORD assistant_db_password assistant_password)
N8N_DB_PASSWORD=$(resolve_value N8N_DB_PASSWORD n8n_db_password n8n_password)

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    -- Auth service database
    CREATE USER auth_user WITH PASSWORD '${AUTH_DB_PASSWORD}';
    CREATE DATABASE auth_db OWNER auth_user;
    GRANT ALL PRIVILEGES ON DATABASE auth_db TO auth_user;

    -- Assistant service database
    CREATE USER assistant_user WITH PASSWORD '${ASSISTANT_DB_PASSWORD}';
    CREATE DATABASE assistant_db OWNER assistant_user;
    GRANT ALL PRIVILEGES ON DATABASE assistant_db TO assistant_user;

    -- n8n database
    CREATE USER n8n_user WITH PASSWORD '${N8N_DB_PASSWORD}';
    CREATE DATABASE n8n_db OWNER n8n_user;
    GRANT ALL PRIVILEGES ON DATABASE n8n_db TO n8n_user;
EOSQL

echo "==> All databases and users created successfully."
