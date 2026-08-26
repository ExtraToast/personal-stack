#!/usr/bin/env bash
# One-time: give vault-bootstrap-auth a Kubernetes auth role so it stops
# depending on a static token that expires unnoticed.
#
# The Job authenticates with a token from the vault-bootstrap-token Secret. That
# token is invalid (`vault token lookup` -> 403), so the Job has been failing,
# its pods are reaped, and every auth role and policy added to bootstrap-auth.sh
# since it expired was applied by Flux and never reached the server. Three
# backups are blocked on exactly that.
#
# This needs a privileged Vault token ONCE. Afterwards the Job logs in with its
# ServiceAccount and there is nothing left to expire.
#
# The companion manifest change is already merged (fleet-infra #128), so the Job
# currently fails with `invalid role name "vault-bootstrap"` -- this script is
# what creates that role. It only adds a policy and a role; it changes nothing
# that is currently working.
set -euo pipefail

CTX="${KUBE_CONTEXT:-personal}"
NS=data-system
SA=vault-bootstrap-auth
ROLE=vault-bootstrap
POLICY=vault-bootstrap
PORT=18200
PF_PID=""

k() { kubectl --context "$CTX" "$@"; }
say() { printf '\n== %s\n' "$*"; }
die() { printf '\nFAILED: %s\n' "$*" >&2; exit 1; }
cleanup() {
  [ -n "$PF_PID" ] && kill "$PF_PID" 2>/dev/null || true
  k delete pod vault-bootstrap-login-probe -n "$NS" --ignore-not-found >/dev/null 2>&1 || true
}
trap cleanup EXIT

say "preflight"
k config current-context >/dev/null || die "context $CTX unreachable"
printf '  context: %s\n' "$(k config current-context)"
k get sa "$SA" -n "$NS" >/dev/null 2>&1 || die "ServiceAccount $NS/$SA not found"
printf '  serviceaccount: %s/%s\n' "$NS" "$SA"

# Vault's TokenReview call needs the SA bound to system:auth-delegator. The Job
# manifest already installs that binding; check rather than assume.
if k get clusterrolebinding vault-bootstrap-auth-token-reviewer >/dev/null 2>&1; then
  printf '  token-reviewer binding: present\n'
else
  printf '  token-reviewer binding: MISSING -- kubernetes auth login will fail\n' >&2
  die "apply cluster/flux/apps/data/vault/bootstrap-auth-job.yaml first"
fi

say "opening a port-forward to vault"
k port-forward -n "$NS" svc/vault "$PORT":8200 >/dev/null 2>&1 &
PF_PID=$!
export VAULT_ADDR="http://127.0.0.1:${PORT}"
ready=no
for _ in $(seq 1 20); do
  if curl -fsS --max-time 2 "${VAULT_ADDR}/v1/sys/health?standbyok=true&sealedcode=200&uninitcode=200" >/dev/null 2>&1; then
    ready=yes; break
  fi
  sleep 2
done
[ "$ready" = yes ] || die "vault is not reachable on $VAULT_ADDR"

sealed="$(curl -fsS "${VAULT_ADDR}/v1/sys/health?standbyok=true&sealedcode=200" | sed -n 's/.*"sealed":\([a-z]*\).*/\1/p')"
printf '  sealed: %s\n' "$sealed"
[ "$sealed" = "false" ] || die "vault is sealed -- unseal before bootstrapping auth"

say "privileged token"
printf 'Paste a Vault token allowed to write policies and auth roles.\n'
printf 'The initial root token works. Input is hidden.\n'
printf 'token: '
read -rs VAULT_TOKEN
printf '\n'
export VAULT_TOKEN
[ -n "$VAULT_TOKEN" ] || die "no token provided"

command -v vault >/dev/null 2>&1 \
  || die "the vault CLI is not installed locally; install it and re-run"
vault token lookup >/dev/null 2>&1 || die "that token is not valid against $VAULT_ADDR"
printf '  token accepted\n'

say "writing policy $POLICY"
# Scoped to what bootstrap-auth.sh actually does: enable the kubernetes auth
# method and the kvv2/database/rabbitmq engines, write the policies and roles it
# defines, manage the auth-api transit signing key, and read the engine admin
# credentials it configures those engines with.
vault policy write "$POLICY" - <<'HCL'
path "sys/auth" {
  capabilities = ["read", "list"]
}
path "sys/auth/*" {
  capabilities = ["create", "update", "read", "sudo"]
}
path "sys/mounts" {
  capabilities = ["read", "list"]
}
path "sys/mounts/*" {
  capabilities = ["create", "update", "read"]
}
path "sys/policies/acl/*" {
  capabilities = ["create", "update", "read"]
}
path "auth/kubernetes/config" {
  capabilities = ["create", "update", "read"]
}
path "auth/kubernetes/role/*" {
  capabilities = ["create", "update", "read"]
}
path "database/config/*" {
  capabilities = ["create", "update", "read"]
}
path "database/roles/*" {
  capabilities = ["create", "update", "read"]
}
path "rabbitmq/config/*" {
  capabilities = ["create", "update", "read"]
}
path "rabbitmq/roles/*" {
  capabilities = ["create", "update", "read"]
}
path "transit/keys/*" {
  capabilities = ["create", "update", "read"]
}
path "kvv2/data/*" {
  capabilities = ["create", "update", "read"]
}
path "kvv2/metadata/*" {
  capabilities = ["create", "update", "read", "list", "delete"]
}
# Engine admin credentials the bootstrap reads to configure database/ and
# rabbitmq/ above. Read-only: the bootstrap never writes these.
path "secret/data/platform/postgres" {
  capabilities = ["read"]
}
path "secret/data/platform/rabbitmq" {
  capabilities = ["read"]
}
HCL
printf '  policy written\n'

say "creating kubernetes auth role $ROLE"
# Bound to this one ServiceAccount in this one namespace. The TTL only has to
# outlast a single bootstrap run.
vault write "auth/kubernetes/role/${ROLE}" \
  bound_service_account_names="$SA" \
  bound_service_account_namespaces="$NS" \
  token_policies="$POLICY" \
  ttl=20m \
  max_ttl=1h >/dev/null
printf '  role bound to %s/%s, ttl=20m\n' "$NS" "$SA"

say "verifying the ServiceAccount can log in"
# The only verification that counts: log in from inside the cluster as that SA,
# the same way the Job will.
k delete pod vault-bootstrap-login-probe -n "$NS" --ignore-not-found >/dev/null 2>&1
cat <<EOF | k apply -f - >/dev/null
apiVersion: v1
kind: Pod
metadata:
  name: vault-bootstrap-login-probe
  namespace: ${NS}
spec:
  serviceAccountName: ${SA}
  restartPolicy: Never
  nodeSelector:
    platform.jorisjonkers.dev/site: frankfurt
  containers:
    - name: probe
      image: hashicorp/vault:1.21.2
      env:
        - name: VAULT_ADDR
          value: http://vault.data-system.svc.cluster.local:8200
      command: ['/bin/sh','-ec']
      args:
        - |
          VAULT_TOKEN="\$(vault write -field=token auth/kubernetes/login \\
            role=${ROLE} \\
            jwt=@/var/run/secrets/kubernetes.io/serviceaccount/token)"
          export VAULT_TOKEN
          echo "login: ok"
          echo "policies: \$(vault token lookup -format=json | grep -o '"${POLICY}"' | head -1)"
          vault policy read vso >/dev/null && echo "policy read: ok"
          vault read -field=bound_service_account_names auth/kubernetes/role/vso >/dev/null && echo "role read: ok"
EOF
phase=""
for _ in $(seq 1 40); do
  phase="$(k get pod vault-bootstrap-login-probe -n "$NS" -o jsonpath='{.status.phase}' 2>/dev/null || true)"
  case "$phase" in Succeeded|Failed) break;; esac
  sleep 3
done
printf '  probe phase: %s\n' "$phase"
k logs vault-bootstrap-login-probe -n "$NS" 2>&1 | sed 's/^/    /'
[ "$phase" = "Succeeded" ] || die "the ServiceAccount could not log in with role $ROLE"

say "done"
printf 'Policy %s and kubernetes auth role %s exist, and %s/%s can log in.\n' \
  "$POLICY" "$ROLE" "$NS" "$SA"
printf '\nThe manifest change is already live (fleet-infra #128), so the Job only\n'
printf 'needs a re-run:\n'
printf '  kubectl --context %s delete job vault-bootstrap-auth -n %s\n' "$CTX" "$NS"
printf '  flux --context %s reconcile kustomization apps-data --with-source\n' "$CTX"
printf '\nThen confirm the roles and policies it was never able to write have landed:\n'
printf '  kubectl --context %s get job vault-bootstrap-auth -n %s\n' "$CTX" "$NS"
printf '  kubectl --context %s get vaultstaticsecret -n observability\n' "$CTX"
printf '\nThe vault-bootstrap-token Secret is then unused and can be deleted.\n'
