#!/usr/bin/env bash
set -euo pipefail

export KUBECONFIG="${HOME}/.kube/personal-stack.yaml"
cd /Users/j.w.jonkers/IDEAProjects/personal-stack-2

VAULT_NS="data-system"
VAULT_POD="vault-0"
VAULT_CONTAINER="vault"

vexec() {
  kubectl -n "$VAULT_NS" exec -c "$VAULT_CONTAINER" "$VAULT_POD" -- \
    env VAULT_ADDR=http://127.0.0.1:8200 "$@"
}

sep() { printf '\n===== %s =====\n' "$1"; }

# shellcheck disable=SC1091
source ./.vault-keys

sep "6. verify ORIGINAL root token still works"
vexec env VAULT_TOKEN="$VAULT_TOKEN" vault token lookup -format=json \
  | jq '{policies: .data.policies, type: .data.type, display_name: .data.display_name}'

sep "7. rekey to generate a NEW unseal key (1/1 shamir)"
NONCE=$(vexec env VAULT_TOKEN="$VAULT_TOKEN" \
  vault operator rekey -init -key-shares=1 -key-threshold=1 -format=json \
  | jq -r '.nonce')
[ -n "$NONCE" ] && [ "$NONCE" != "null" ] || { echo "rekey init failed"; exit 1; }
NEW_KEY=$(vexec env VAULT_TOKEN="$VAULT_TOKEN" \
  vault operator rekey -nonce="$NONCE" -format=json "$VAULT_UNSEAL_KEY" \
  | jq -r '.keys_base64[0]')
[ -n "$NEW_KEY" ] && [ "$NEW_KEY" != "null" ] || { echo "rekey completion failed"; exit 1; }
echo "new unseal key minted (length=$(printf '%s' "$NEW_KEY" | wc -c))"

sep "8. verify NEW key: seal then unseal with new key"
vexec env VAULT_TOKEN="$VAULT_TOKEN" vault operator seal
vexec vault operator unseal "$NEW_KEY" >/dev/null
vexec vault status | grep -E "Initialized|Sealed"

sep "9. write new unseal key + existing root token to .vault-keys"
umask 077
cat > .vault-keys <<EOF
VAULT_UNSEAL_KEY='${NEW_KEY}'
VAULT_TOKEN='${VAULT_TOKEN}'
EOF
chmod 600 .vault-keys
echo ".vault-keys updated ($(wc -c < .vault-keys) bytes)"

sep "10. cleanup in-memory secrets"
unset VAULT_UNSEAL_KEY VAULT_TOKEN NONCE NEW_KEY

echo
echo "Vault restore + rekey complete."
