#!/bin/sh
# stalwart-apply sidecar: reconciles the declarative settings stalwart
# stores in its datastore (listeners, domain wiring, ACME/DNS providers,
# Vault-managed accounts) and renews secret-bearing values (the
# Cloudflare DNS-01 token) on every pod start. Runs alongside the
# stalwart container; a VaultStaticSecret rolloutRestartTarget restarts
# the pod when a secret rotates, so this re-runs with the fresh value.
#
# Idempotent: pure-create settings come from the plan template and are
# applied only when absent; the domain wiring, hostname, CF token and
# auth account are reconciled with query-then-update so they converge
# regardless of the server-assigned ids in the datastore.
set -eu

: "${STALWART_URL:=http://127.0.0.1:8080}"
: "${STALWART_USER:?}"
: "${STALWART_PASSWORD:?}"
: "${STALWART_DOMAIN:?}"
: "${STALWART_HOSTNAME:?}"
: "${CF_DNS_API_TOKEN:?}"
: "${STALWART_CLI_VERSION:=1.0.7}"
: "${PLAN_TEMPLATE:=/opt/stalwart-tools/plan.ndjson.tmpl}"
: "${ACCOUNTS_FILE:=/opt/stalwart-tools/accounts.json}"
# Optional: catch-all address for unknown local recipients of the
# deployment domain. Empty/unset is a no-op (a manually-set value is
# never cleared).
: "${STALWART_CATCHALL:=}"

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
    echo "apply: domain ${STALWART_DOMAIN} not found in the datastore" >&2
    return 1
  fi

  # 2. Hostname, default domain, and the MX host published in DNS
  #     (idempotent). `mailExchangers` is the list Stalwart publishes as
  #     the domain's MX records (crates/common/src/network/dns/records.rs
  #     iterates `system.mail_exchangers`). At first boot Stalwart seeds a
  #     single entry whose hostname is the container OS hostname — in k8s
  #     the pod name — and persists it, so the published MX is a bogus,
  #     unresolvable pod name (e.g. `stalwart-78587fc555-dfdr5.`) that
  #     also rots on every pod roll and is never corrected by setting
  #     `defaultHostname` alone. Pin the single MX entry to the stable
  #     public FQDN at priority 10 so inbound mail can resolve the
  #     server. `mailExchangers` is a mutable SystemSettings field, so
  #     this converges on every reconcile (objectList values are encoded
  #     as index-keyed maps by the apply API).
  printf '{"@type":"update","object":"SystemSettings","value":{"defaultHostname":"%s","defaultDomainId":"%s","mailExchangers":{"0":{"hostname":"%s","priority":10}}}}\n' \
    "$STALWART_HOSTNAME" "$dom" "$STALWART_HOSTNAME" | sc apply --file /dev/stdin

  # 3. Wire the domain to automatic ACME + Cloudflare DNS-01, and force a
  #     DNS (re)publish. Stalwart enqueues the DNS publish task only when
  #     dns_management TRANSITIONS into Automatic
  #     (crates/jmap/src/registry/mapping/domain.rs: the task is scheduled
  #     under `old.is_none_or(|old| !matches!(old.dns_management,
  #     Automatic))`). Re-asserting Automatic when it is already Automatic
  #     is a no-op, so a corrected mailExchangers/MX (step 2) is computed
  #     but never pushed to Cloudflare — which is why the bogus pod-name MX
  #     persisted across rolls. Flip to Manual first (which schedules no
  #     task and never deletes records) then back to Automatic to force the
  #     transition; that republishes the full record set, including the now
  #     correct MX, on every reconcile (self-healing, idempotent — DKIM
  #     rotation and cert issuance trigger only on their own transitions /
  #     first domain creation, not here).
  printf '{"@type":"update","object":"Domain","id":"%s","value":{"dnsManagement":{"@type":"Manual"}}}\n' \
    "$dom" | sc apply --file /dev/stdin
  printf '{"@type":"update","object":"Domain","id":"%s","value":{"certificateManagement":{"@type":"Automatic","acmeProviderId":"%s"},"dnsManagement":{"@type":"Automatic","dnsServerId":"%s"}}}\n' \
    "$dom" "$acme" "$dns" | sc apply --file /dev/stdin

  # 4. Renew the Cloudflare DNS-01 token every boot.
  printf '{"@type":"update","object":"DnsServer","id":"%s","value":{"secret":{"@type":"Value","secret":"%s"}}}\n' \
    "$dns" "$CF_DNS_API_TOKEN" | sc apply --file /dev/stdin

  # 4b. Catch-all address for unknown local recipients (declarative,
  #     env-driven). Empty/unset is a no-op so a manually-set catch-all
  #     is never cleared. Applied as a separate partial Domain update so
  #     it only touches catchAllAddress and never disturbs the cert/DNS
  #     wiring above.
  if [ -n "$STALWART_CATCHALL" ]; then
    echo "apply: setting catch-all for ${STALWART_DOMAIN} to ${STALWART_CATCHALL}"
    jq -nc --arg id "$dom" --arg addr "$STALWART_CATCHALL" \
      '{"@type":"update","object":"Domain",id:$id,value:{catchAllAddress:$addr}}' \
      | sc apply --file /dev/stdin
  fi

  # 5. Reconcile Vault-managed accounts (passwords, aliases, group
  #    memberships) from accounts.json. Update-only for accounts that
  #    already exist — never delete and never recreate — so the mailbox
  #    an account already links to is never disturbed. Only list
  #    accounts whose full alias/group set is declared here and whose
  #    password lives in Vault; user-managed mailboxes must NOT appear,
  #    or their state would be overwritten on every boot.
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

    # Always set the password. aliases/memberGroupIds/description are only
    # touched when the entry explicitly declares them, so password-only
    # entries never clear values the webadmin set.
    fields="$(jq -nc --arg pw "$pw" '{credentials:{"0":{"@type":"Password",secret:$pw}}}')"

    # displayName -> the account's `description` (the human full name
    # shown in the webadmin and used as the From display name).
    if printf '%s' "$entry" | jq -e 'has("displayName")' >/dev/null; then
      dn="$(printf '%s' "$entry" | jq -r '.displayName')"
      fields="$(printf '%s' "$fields" | jq -c --arg dn "$dn" '. + {description:$dn}')"
    fi

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
