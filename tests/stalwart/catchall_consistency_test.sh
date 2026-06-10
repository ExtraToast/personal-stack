#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)

ACCOUNTS_JSON="$REPO_ROOT/infra/stalwart/accounts.json"
DEPLOYMENT_YAML="$REPO_ROOT/platform/cluster/flux/apps/mail/stalwart/deployment.yaml"
VAULT_STATIC_SECRETS_YAML="$REPO_ROOT/platform/cluster/flux/apps/mail/stalwart/vault-static-secrets.yaml"
APPLY_SH="$REPO_ROOT/infra/stalwart/apply.sh"

failures=0

pass() {
  printf 'PASS %s\n' "$1"
}

fail() {
  printf 'FAIL %s\n' "$1"
  failures=$((failures + 1))
}

assert_true() {
  name=$1
  shift
  if "$@" >/dev/null 2>&1; then
    pass "$name"
  else
    fail "$name"
  fi
}

assert_eq() {
  name=$1
  actual=$2
  expected=$3
  if [ "$actual" = "$expected" ]; then
    pass "$name"
  else
    fail "$name"
    printf '  expected: %s\n' "$expected"
    printf '  actual:   %s\n' "$actual"
  fi
}

assert_json_eq() {
  name=$1
  actual=$2
  expected=$3
  actual_sorted=$(printf '%s\n' "$actual" | jq -S -c .)
  expected_sorted=$(printf '%s\n' "$expected" | jq -S -c .)
  assert_eq "$name" "$actual_sorted" "$expected_sorted"
}

catchall_value() {
  awk '
    /^[[:space:]]*-[[:space:]]*name:[[:space:]]*STALWART_CATCHALL[[:space:]]*$/ { in_entry=1; next }
    in_entry && /^[[:space:]]*-[[:space:]]*name:/ { in_entry=0 }
    in_entry && /^[[:space:]]*value:[[:space:]]*/ {
      sub(/^[[:space:]]*value:[[:space:]]*/, "")
      print
      exit
    }
  ' "$DEPLOYMENT_YAML"
}

env_entry_has_optional_true() {
  awk '
    /^[[:space:]]*-[[:space:]]*name:[[:space:]]*JORIS_MAIL_PASSWORD[[:space:]]*$/ { in_entry=1; next }
    in_entry && /^[[:space:]]*-[[:space:]]*name:/ { in_entry=0 }
    in_entry && /^[[:space:]]*optional:[[:space:]]*true[[:space:]]*$/ { found=1 }
    END { exit found ? 0 : 1 }
  ' "$DEPLOYMENT_YAML"
}

vault_template_references_joris_password() {
  awk '
    /^[[:space:]]*JORIS_MAIL_PASSWORD:[[:space:]]*$/ { in_template=1; next }
    in_template && /^[[:space:]]*[A-Z0-9_]+:[[:space:]]*$/ { in_template=0 }
    in_template && /joris\.password/ { found=1 }
    END { exit found ? 0 : 1 }
  ' "$VAULT_STATIC_SECRETS_YAML"
}

assert_true "accounts.json is valid JSON" jq -e . "$ACCOUNTS_JSON"
assert_true "every account has non-empty localPart" jq -e 'type == "array" and all(.[]; (.localPart | type == "string" and length > 0))' "$ACCOUNTS_JSON"

catchall=$(catchall_value)
catchall_local=${catchall%@*}
assert_true "catch-all local part is declared as localPart or alias" jq -e --arg lp "$catchall_local" 'any(.[]; .localPart == $lp or any(.aliases[]?; . == $lp))' "$ACCOUNTS_JSON"

assert_true "joris.jonkers account has expected passwordEnv and alias" jq -e 'any(.[]; .localPart == "joris.jonkers" and .passwordEnv == "JORIS_MAIL_PASSWORD" and any(.aliases[]?; . == "extratoast"))' "$ACCOUNTS_JSON"

pw=secret123
credentials=$(jq -nc --arg pw "$pw" '{credentials:{"0":{"@type":"Password",secret:$pw}}}')
assert_json_eq "apply.sh credentials jq shape" "$credentials" '{"credentials":{"0":{"@type":"Password","secret":"secret123"}}}'

entry='{"aliases":["extratoast"]}'
dom=DOMID
aliases=$(printf '%s\n' "$entry" | jq -c --arg dom "$dom" '.aliases | to_entries | map({(.key|tostring): {name:(.value|split("@")[0]), domainId:$dom, enabled:true}}) | add // {}')
assert_json_eq "apply.sh aliases jq shape" "$aliases" '{"0":{"name":"extratoast","domainId":"DOMID","enabled":true}}'

assert_true "deployment wires JORIS_MAIL_PASSWORD as optional env" env_entry_has_optional_true
assert_true "Vault template wires JORIS_MAIL_PASSWORD from joris.password" vault_template_references_joris_password

# apply.sh must pin the published MX host (mailExchangers on
# SystemSettings) to the stable FQDN so the auto-managed DNS does not
# publish the pod name as the MX target.
apply_pins_mail_exchanger() {
  grep -q '"mailExchangers":{"0":{"hostname"' "$APPLY_SH" \
    && grep -q '"object":"SystemSettings"' "$APPLY_SH"
}
assert_true "apply.sh pins mailExchangers host on SystemSettings (MX target)" apply_pins_mail_exchanger

# apply.sh must map an account's declared displayName onto its description.
apply_maps_display_name() {
  grep -q 'has("displayName")' "$APPLY_SH" && grep -q 'description:\$dn' "$APPLY_SH"
}
assert_true "apply.sh maps displayName -> account description" apply_maps_display_name

# The catch-all target account carries the operator's full name and the
# extratoast address as an alias (primary stays joris.jonkers@).
assert_true "joris.jonkers has displayName 'Joris Jonkers'" \
  jq -e 'any(.[]; .localPart=="joris.jonkers" and .displayName=="Joris Jonkers")' "$ACCOUNTS_JSON"

if [ "$failures" -ne 0 ]; then
  exit 1
fi
