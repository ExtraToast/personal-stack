#!/bin/sh
# stalwart-apply sidecar: reconciles the declarative settings that
# migrate_v016.py does not carry, and renews secret-bearing values
# (the Cloudflare DNS-01 token) on every pod start. Runs alongside the
# stalwart container; a VaultStaticSecret rolloutRestartTarget restarts
# the pod when a secret rotates, so this re-runs with the fresh value.
#
# Idempotent: pure-create settings come from the plan template and are
# applied only when absent; the domain wiring, hostname, CF token and
# auth account are reconciled with query-then-update so they converge
# regardless of the server-assigned ids the migration produced.
set -eu

: "${STALWART_URL:=http://127.0.0.1:8080}"
: "${STALWART_USER:?}"
: "${STALWART_PASSWORD:?}"
: "${STALWART_DOMAIN:?}"
: "${STALWART_HOSTNAME:?}"
: "${CF_DNS_API_TOKEN:?}"
: "${STALWART_CLI_VERSION:=1.0.7}"
: "${PLAN_TEMPLATE:=/plan/plan.ndjson.tmpl}"
: "${AUTH_MAIL_USERNAME:=auth}"
: "${AUTH_MAIL_PASSWORD:?}"

export STALWART_URL STALWART_USER STALWART_PASSWORD

install_cli() {
  command -v stalwart-cli >/dev/null 2>&1 && return
  arch="$(uname -m)"
  case "$arch" in
    x86_64) target=x86_64-unknown-linux-musl ;;
    aarch64) target=aarch64-unknown-linux-musl ;;
    *) echo "unsupported arch: $arch" >&2; exit 1 ;;
  esac
  echo "apply: downloading stalwart-cli v${STALWART_CLI_VERSION} (${target})"
  curl -fsSL "https://github.com/stalwartlabs/cli/releases/download/v${STALWART_CLI_VERSION}/stalwart-cli-${target}.tar.xz" -o /tmp/cli.tar.xz
  tar -xJf /tmp/cli.tar.xz -C /tmp
  find /tmp -type f -name stalwart-cli -exec mv {} /usr/local/bin/stalwart-cli \;
  chmod +x /usr/local/bin/stalwart-cli
}

sc() { stalwart-cli "$@"; }

# `query --json` emits NDJSON (one object per line). Slurp with `jq -s`
# so the parse is robust for zero, one, or many results.

# id of the first object of a type, or empty string.
first_id() {
  sc query "$1" --json 2>/dev/null | jq -rs '.[0].id // empty'
}

# id of the object of a type whose `name` matches, or empty string.
id_by_name() {
  sc query "$1" --json 2>/dev/null | jq -rs --arg n "$2" 'map(select(.name==$n))[0].id // empty'
}

# id of the Account whose emailAddress matches (the Account list
# projection exposes emailAddress, not name), or empty string.
id_by_email() {
  sc query Account --json 2>/dev/null | jq -rs --arg e "$1" 'map(select(.emailAddress==$e))[0].id // empty'
}

wait_ready() {
  echo "apply: waiting for ${STALWART_URL}/admin/ ..."
  i=0
  until curl -fsS -o /dev/null "${STALWART_URL}/admin/"; do
    i=$((i + 1))
    [ "$i" -gt 120 ] && { echo "apply: stalwart never became ready" >&2; exit 1; }
    sleep 2
  done
}

reconcile() {
  # 1. Pure-create settings (DnsServer, AcmeProvider, plaintext listeners).
  if [ -z "$(first_id AcmeProvider)" ]; then
    echo "apply: applying base settings plan"
    envsubst < "$PLAN_TEMPLATE" > /tmp/plan.ndjson
    sc apply --file /tmp/plan.ndjson
  fi

  dom="$(id_by_name Domain "${STALWART_DOMAIN}")"
  acme="$(first_id AcmeProvider)"
  dns="$(first_id DnsServer)"

  if [ -z "$dom" ]; then
    echo "apply: domain ${STALWART_DOMAIN} not found; migration export not yet applied" >&2
    return 1
  fi

  # 2. Hostname + default domain (idempotent).
  printf '{"@type":"update","object":"SystemSettings","value":{"defaultHostname":"%s","defaultDomainId":"%s"}}\n' \
    "$STALWART_HOSTNAME" "$dom" | sc apply --file /dev/stdin

  # 3. Wire the migrated domain to automatic ACME + Cloudflare DNS.
  printf '{"@type":"update","object":"Domain","id":"%s","value":{"certificateManagement":{"@type":"Automatic","acmeProviderId":"%s"},"dnsManagement":{"@type":"Automatic","dnsServerId":"%s"}}}\n' \
    "$dom" "$acme" "$dns" | sc apply --file /dev/stdin

  # 4. Renew the Cloudflare DNS-01 token every boot.
  printf '{"@type":"update","object":"DnsServer","id":"%s","value":{"secret":{"@type":"Value","secret":"%s"}}}\n' \
    "$dns" "$CF_DNS_API_TOKEN" | sc apply --file /dev/stdin

  # 5. auth-api SMTP submission account (replaces the v0.15 principal job).
  # credentials is an objectList, encoded by the apply API as an
  # index-keyed map, not a JSON array.
  acct="$(id_by_email "${AUTH_MAIL_USERNAME}@${STALWART_DOMAIN}")"
  if [ -z "$acct" ]; then
    printf '{"@type":"create","object":"Account","value":{"acct-auth":{"@type":"User","name":"%s","domainId":"%s","credentials":{"0":{"@type":"Password","secret":"%s"}}}}}\n' \
      "$AUTH_MAIL_USERNAME" "$dom" "$AUTH_MAIL_PASSWORD" | sc apply --file /dev/stdin
  else
    printf '{"@type":"update","object":"Account","id":"%s","value":{"credentials":{"0":{"@type":"Password","secret":"%s"}}}}\n' \
      "$acct" "$AUTH_MAIL_PASSWORD" | sc apply --file /dev/stdin
  fi

  echo "apply: reconcile complete"
}

install_cli
wait_ready
reconcile
echo "apply: idling (re-runs on next pod start)"
exec sleep infinity
