#!/usr/bin/env bash

set -Eeuo pipefail

compose_args=(-f docker-compose.yml -f docker-compose.ci.yml)

echo "Starting long-lived system test services..."
docker compose "${compose_args[@]}" up -d --no-build --wait --timeout 300

echo "Bootstrapping Vault OIDC configuration..."
docker compose "${compose_args[@]}" --profile bootstrap rm -fsv vault-oidc-init >/dev/null 2>&1 || true
docker compose "${compose_args[@]}" --profile bootstrap up --no-build --force-recreate --exit-code-from vault-oidc-init vault-oidc-init
