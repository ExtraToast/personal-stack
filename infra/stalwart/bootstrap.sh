#!/bin/sh
# Applies the declarative configuration plan against a freshly-booted
# v0.16 Stalwart. v0.16 keeps no TOML: config.json holds only the
# datastore, everything else is a JMAP object reconciled via
# `stalwart-cli apply`. The `create` operations in the plan are not
# idempotent (a second run raises primaryKeyViolation), so the apply is
# guarded on whether the deployment domain already exists.
set -eu

: "${STALWART_URL:=http://stalwart:8080}"
: "${STALWART_USER:=admin}"
: "${STALWART_PASSWORD:?STALWART_PASSWORD must be set}"
: "${STALWART_PLAN:=/opt/stalwart-tools/plan.dev.ndjson}"
: "${STALWART_DOMAIN:=jorisjonkers.test}"
# Optional: catch-all address for unknown local recipients of the dev
# domain. Empty (the default) is a no-op (an existing value is never
# cleared); set it in the compose env to exercise catch-all in dev.
: "${STALWART_CATCHALL:=}"

export STALWART_URL STALWART_USER STALWART_PASSWORD

echo "bootstrap: waiting for stalwart webadmin at ${STALWART_URL}/admin/ ..."
i=0
until curl -fsS -o /dev/null "${STALWART_URL}/admin/"; do
  i=$((i + 1))
  if [ "$i" -gt 60 ]; then
    echo "bootstrap: stalwart did not become ready in time" >&2
    exit 1
  fi
  sleep 2
done

if stalwart-cli query Domain --where "name=${STALWART_DOMAIN}" 2>/dev/null | grep -q "${STALWART_DOMAIN}"; then
  echo "bootstrap: ${STALWART_DOMAIN} already present; skipping non-idempotent create plan"
else
  echo "bootstrap: applying declarative plan ${STALWART_PLAN} ..."
  stalwart-cli apply --file "${STALWART_PLAN}"
fi

# Catch-all for unknown local recipients (env-driven; empty = no-op so a
# manually-set value is never cleared). Idempotent partial Domain update,
# applied whether or not the create plan ran this boot.
if [ -n "$STALWART_CATCHALL" ]; then
  dom="$(stalwart-cli query Domain --json 2>/dev/null \
    | jq -rs --arg n "$STALWART_DOMAIN" 'map(select(.name==$n))[0].id // empty')"
  if [ -z "$dom" ]; then
    echo "bootstrap: domain ${STALWART_DOMAIN} not found; cannot set catch-all" >&2
    exit 1
  fi
  echo "bootstrap: setting catch-all for ${STALWART_DOMAIN} to ${STALWART_CATCHALL}"
  jq -nc --arg id "$dom" --arg addr "$STALWART_CATCHALL" \
    '{"@type":"update","object":"Domain",id:$id,value:{catchAllAddress:$addr}}' \
    | stalwart-cli apply --file /dev/stdin
fi

echo "bootstrap: done"
