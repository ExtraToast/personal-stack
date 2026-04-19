#!/usr/bin/env bash
# Seed a stalwart mail account for auth-api email verification in CI.
set -euo pipefail

STALWART_ADDR="${STALWART_ADDR:-http://localhost:8080}"
ADMIN_USER="${STALWART_ADMIN_USER:-admin}"
ADMIN_PASS="${STALWART_ADMIN_PASSWORD:-stalwart-dev-admin}"
MAIL_PASSWORD="${STALWART_MAIL_PASSWORD:-ci_stalwart_mail_pass}"
DOMAIN="${DOMAIN:-jorisjonkers.test}"

echo "==> Seeding stalwart mail account: auth@${DOMAIN}"
curl -sf -u "${ADMIN_USER}:${ADMIN_PASS}" \
  "${STALWART_ADDR}/api/principal" \
  -H "Content-Type: application/json" \
  -d '{
    "type": "individual",
    "name": "auth",
    "secrets": ["'"${MAIL_PASSWORD}"'"],
    "emails": ["auth@'"${DOMAIN}"'"]
  }' || echo "Warning: failed to seed stalwart account (may already exist)"
