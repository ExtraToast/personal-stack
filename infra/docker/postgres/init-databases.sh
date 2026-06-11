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

AUTH_DB_USER=$(resolve_value AUTH_DB_USER auth_db_user auth_user)
AUTH_DB_PASSWORD=$(resolve_value AUTH_DB_PASSWORD auth_db_password auth_password)
AGENTS_DB_USER=$(resolve_value AGENTS_DB_USER agents_db_user agents_user)
AGENTS_DB_PASSWORD=$(resolve_value AGENTS_DB_PASSWORD agents_db_password agents_password)
N8N_DB_USER=$(resolve_value N8N_DB_USER n8n_db_user n8n_user)
N8N_DB_PASSWORD=$(resolve_value N8N_DB_PASSWORD n8n_db_password n8n_password)

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    -- Auth service database
    CREATE USER ${AUTH_DB_USER} WITH PASSWORD '${AUTH_DB_PASSWORD}';
    CREATE DATABASE auth_db OWNER ${AUTH_DB_USER};
    GRANT ALL PRIVILEGES ON DATABASE auth_db TO ${AUTH_DB_USER};

    -- Agents service database
    CREATE USER ${AGENTS_DB_USER} WITH PASSWORD '${AGENTS_DB_PASSWORD}';
    CREATE DATABASE agents_db OWNER ${AGENTS_DB_USER};
    GRANT ALL PRIVILEGES ON DATABASE agents_db TO ${AGENTS_DB_USER};

    -- n8n database
    CREATE USER ${N8N_DB_USER} WITH PASSWORD '${N8N_DB_PASSWORD}';
    CREATE DATABASE n8n_db OWNER ${N8N_DB_USER};
    GRANT ALL PRIVILEGES ON DATABASE n8n_db TO ${N8N_DB_USER};
EOSQL

# PostgreSQL 15+ revoked default CREATE on the public schema for non-superusers.
# Transfer public schema ownership so each service can run migrations.
for db_and_user in "auth_db:${AUTH_DB_USER}" "agents_db:${AGENTS_DB_USER}" "n8n_db:${N8N_DB_USER}"; do
    db="${db_and_user%%:*}"
    user="${db_and_user##*:}"
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$db" <<-EOSQL
        ALTER SCHEMA public OWNER TO ${user};
EOSQL
done

echo "==> All databases and users created successfully."
