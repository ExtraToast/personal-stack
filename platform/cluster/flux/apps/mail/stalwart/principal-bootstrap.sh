#!/usr/bin/env sh
# Provision the `auth` principal in Stalwart so auth-api can submit
# mail via SMTP AUTH on stalwart.mail-system.svc.cluster.local:587.
#
# Reads credentials from /vault/secrets/principal.env (rendered by
# Vault Agent inside this Job's pod). Idempotent:
#   - principal missing → POST creates it.
#   - principal exists  → PATCH updates the secret so the password
#                         tracks whatever auth-api currently uses.
# Anything else fails the Job, which surfaces in Flux/HelmRelease
# status.
set -eu

. /vault/secrets/principal.env

STALWART_ADDR="http://stalwart.mail-system.svc.cluster.local:8080"
PRINCIPAL="auth"
EMAIL="${MAIL_USERNAME:?MAIL_USERNAME not set by Vault Agent}"
SECRET="${MAIL_PASSWORD:?MAIL_PASSWORD not set by Vault Agent}"
ADMIN_USER="${STALWART_ADMIN_USER:?STALWART_ADMIN_USER not set}"
ADMIN_PASS="${STALWART_ADMIN_SECRET:?STALWART_ADMIN_SECRET not set}"

auth_curl() {
  curl --silent --show-error --user "$ADMIN_USER:$ADMIN_PASS" "$@"
}

create_payload() {
  cat <<JSON
{"type":"individual","name":"$PRINCIPAL","emails":["$EMAIL"],"secrets":["$SECRET"]}
JSON
}

patch_secrets_payload() {
  cat <<JSON
{"secrets":["$SECRET"],"emails":["$EMAIL"]}
JSON
}

echo "==> Checking Stalwart principal '$PRINCIPAL' at $STALWART_ADDR"
status=$(
  auth_curl --output /tmp/get.body --write-out '%{http_code}' \
    "$STALWART_ADDR/api/principal/$PRINCIPAL" || true
)
echo "GET /api/principal/$PRINCIPAL -> HTTP $status"

case "$status" in
  200)
    echo "==> Principal exists; PATCHing email + secret to current values"
    auth_curl --fail --output /tmp/patch.body --write-out '%{http_code}' \
      --request PATCH \
      --header 'Content-Type: application/json' \
      --data "$(patch_secrets_payload)" \
      "$STALWART_ADDR/api/principal/$PRINCIPAL" > /tmp/patch.code
    code=$(cat /tmp/patch.code)
    echo "PATCH /api/principal/$PRINCIPAL -> HTTP $code"
    case "$code" in
      2*) echo "OK"; exit 0 ;;
      *) echo "PATCH failed:"; cat /tmp/patch.body; exit 1 ;;
    esac
    ;;
  404)
    echo "==> Principal missing; POSTing"
    auth_curl --output /tmp/post.body --write-out '%{http_code}' \
      --request POST \
      --header 'Content-Type: application/json' \
      --data "$(create_payload)" \
      "$STALWART_ADDR/api/principal" > /tmp/post.code
    code=$(cat /tmp/post.code)
    echo "POST /api/principal -> HTTP $code"
    case "$code" in
      2*) echo "OK"; exit 0 ;;
      *) echo "POST failed:"; cat /tmp/post.body; exit 1 ;;
    esac
    ;;
  *)
    echo "Unexpected status from Stalwart admin API: $status"
    cat /tmp/get.body
    exit 1
    ;;
esac
