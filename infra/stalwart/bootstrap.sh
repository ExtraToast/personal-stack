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
: "${STALWART_PLAN:=/plan/plan.ndjson}"
: "${STALWART_DOMAIN:=jorisjonkers.test}"

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
  echo "bootstrap: ${STALWART_DOMAIN} already present; configuration already applied, nothing to do"
  exit 0
fi

echo "bootstrap: applying declarative plan ${STALWART_PLAN} ..."
stalwart-cli apply --file "${STALWART_PLAN}"
echo "bootstrap: done"
