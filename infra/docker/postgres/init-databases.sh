#!/usr/bin/env bash
set -euo pipefail

# This script is executed by the PostgreSQL entrypoint on first run.
# It creates separate databases and users for each service.

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    -- Auth service database
    CREATE USER auth_user WITH PASSWORD 'auth_password';
    CREATE DATABASE auth_db OWNER auth_user;
    GRANT ALL PRIVILEGES ON DATABASE auth_db TO auth_user;

    -- Assistant service database
    CREATE USER assistant_user WITH PASSWORD 'assistant_password';
    CREATE DATABASE assistant_db OWNER assistant_user;
    GRANT ALL PRIVILEGES ON DATABASE assistant_db TO assistant_user;

    -- n8n database
    CREATE USER n8n_user WITH PASSWORD 'n8n_password';
    CREATE DATABASE n8n_db OWNER n8n_user;
    GRANT ALL PRIVILEGES ON DATABASE n8n_db TO n8n_user;
EOSQL

echo "==> All databases and users created successfully."
