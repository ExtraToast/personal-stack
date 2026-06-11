#!/usr/bin/env bash
# Verify that the expected database migrations ran in the CI compose stack.

set -Eeuo pipefail

PGPASSWORD=ci_auth_pass psql -h 127.0.0.1 -U auth_user -d auth_db -c '\dt' | grep -q app_user || {
  echo "auth_db migrations missing" >&2
  exit 1
}

PGPASSWORD=ci_agents_pass psql -h 127.0.0.1 -U agents_user -d agents_db -c '\dt' | grep -q conversation || {
  echo "agents_db migrations missing" >&2
  exit 1
}

echo "==> Database migrations verified"
