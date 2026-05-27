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
: "${PLAN_TEMPLATE:=/apply/plan.ndjson.tmpl}"
: "${ACCOUNTS_FILE:=/apply/accounts.json}"

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

  # 5. Reconcile Vault-managed accounts (passwords, aliases, group
  #    memberships) from accounts.json. Update-only for accounts that
  #    already exist — never delete and never recreate — so the mailbox
  #    a migrated account links to (restore-<id>) is never disturbed.
  #    Only list accounts whose full alias/group set is declared here and
  #    whose password lives in Vault; user-managed mailboxes must NOT
  #    appear, or their state would be overwritten on every boot.
  reconcile_accounts "$dom"

  echo "apply: reconcile complete"
}

# objectList/set values are encoded by the apply API as index-keyed maps,
# not JSON arrays.
reconcile_accounts() {
  # $1 is the Domain object id (used for domainId references); email
  # addresses are formed from the domain NAME in $STALWART_DOMAIN.
  dom="$1"
  count="$(jq 'length' "$ACCOUNTS_FILE")"
  i=0
  while [ "$i" -lt "$count" ]; do
    entry="$(jq -c ".[$i]" "$ACCOUNTS_FILE")"
    i=$((i + 1))
    lp="$(printf '%s' "$entry" | jq -r '.localPart')"
    pwenv="$(printf '%s' "$entry" | jq -r '.passwordEnv')"
    pw="$(eval printf '%s' "\"\${$pwenv:-}\"")"
    if [ -z "$pw" ]; then
      echo "apply: skipping ${lp}: \$${pwenv} is empty" >&2
      continue
    fi

    # Always set the password. aliases/memberGroupIds are only touched
    # when the entry explicitly declares them, so password-only entries
    # never clear aliases/groups that the migration (or the webadmin) set.
    fields="$(jq -nc --arg pw "$pw" '{credentials:{"0":{"@type":"Password",secret:$pw}}}')"

    if printf '%s' "$entry" | jq -e 'has("aliases")' >/dev/null; then
      aliases="$(printf '%s' "$entry" | jq -c --arg dom "$dom" \
        '.aliases | to_entries
          | map({(.key|tostring): {name:(.value|split("@")[0]), domainId:$dom, enabled:true}})
          | add // {}')"
      fields="$(printf '%s' "$fields" | jq -c --argjson a "$aliases" '. + {aliases:$a}')"
    fi

    if printf '%s' "$entry" | jq -e 'has("groups")' >/dev/null; then
      gids='{}'
      for g in $(printf '%s' "$entry" | jq -r '.groups[]? // empty'); do
        gid="$(id_by_email "${g}@${STALWART_DOMAIN}")"
        [ -n "$gid" ] && gids="$(printf '%s' "$gids" | jq -c --arg id "$gid" '. + {($id):true}')"
      done
      fields="$(printf '%s' "$fields" | jq -c --argjson g "$gids" '. + {memberGroupIds:$g}')"
    fi

    acct="$(id_by_email "${lp}@${STALWART_DOMAIN}")"
    if [ -z "$acct" ]; then
      echo "apply: creating account ${lp}@${STALWART_DOMAIN}"
      jq -nc --arg lp "$lp" --arg dom "$dom" --argjson f "$fields" \
        '{"@type":"create","object":"Account","value":{"acct":({"@type":"User",name:$lp,domainId:$dom}+$f)}}' \
        | sc apply --file /dev/stdin
    else
      echo "apply: updating account ${lp}@${STALWART_DOMAIN}"
      jq -nc --arg id "$acct" --argjson f "$fields" \
        '{"@type":"update","object":"Account",id:$id,value:$f}' \
        | sc apply --file /dev/stdin
    fi
  done
}

install_cli
wait_ready
reconcile
echo "apply: idling (re-runs on next pod start)"
exec sleep infinity
