#!/usr/bin/env bash
###############################################################################
# The three things left after the Flux source cutover that need credentials or
# a decision, in one pass:
#
#   1. Discord webhook URL  -> secret/platform/observability discord.alert_webhook_url
#      Without it Alertmanager has a receiver with no destination.
#   2. Vault metrics token  -> secret/platform/observability vault.prometheus_token
#      Expired. While it is dead Vault's /sys/metrics scrape 403s, which means
#      VaultSealed cannot fire: its input series simply does not exist.
#   3. The four agents CronJobs failing on the revoked Claude OAuth token.
#      Optional, opt-in, see --suspend-agent-cronjobs.
#
# Nothing is echoed: secrets are read with a silent prompt and passed to vault
# without appearing in output or shell history.
#
# Usage:
#   bash post-cutover-fixups.sh
#   bash post-cutover-fixups.sh --suspend-agent-cronjobs
#   SKIP_WEBHOOK=1 bash post-cutover-fixups.sh      # only the metrics token
#   SKIP_TOKEN=1   bash post-cutover-fixups.sh      # only the webhook
###############################################################################
set -uo pipefail

CTX="${KUBE_CONTEXT:-personal}"
VAULT_LOCAL_PORT="${VAULT_LOCAL_PORT:-8200}"
PROM_LOCAL_PORT="${PROM_LOCAL_PORT:-19490}"
SUSPEND_CRONJOBS=0
[ "${1:-}" = "--suspend-agent-cronjobs" ] && SUSPEND_CRONJOBS=1

k() { kubectl --context "$CTX" "$@"; }
PF_PIDS=""
cleanup() { for p in $PF_PIDS; do kill "$p" 2>/dev/null; done; }
trap cleanup EXIT

echo "=============================================================="
echo " 0. port-forward Vault (forward-auth blocks the CLI directly)"
echo "=============================================================="
k port-forward -n data-system svc/vault "${VAULT_LOCAL_PORT}:8200" >/dev/null 2>&1 &
PF_PIDS="$PF_PIDS $!"
export VAULT_ADDR="http://127.0.0.1:${VAULT_LOCAL_PORT}"
# --retry-connrefused waits for the tunnel without a fixed sleep.
if ! curl -fsS --retry 20 --retry-connrefused --retry-delay 1 \
     "${VAULT_ADDR}/v1/sys/health?standbyok=true&sealedcode=200&uninitcode=200" >/dev/null 2>&1; then
  echo "   ABORT: cannot reach Vault at $VAULT_ADDR"; exit 1
fi
echo "   reachable at $VAULT_ADDR"

if [ "$(curl -fsS "${VAULT_ADDR}/v1/sys/seal-status" | sed -n 's/.*"sealed":\([a-z]*\).*/\1/p')" = "true" ]; then
  echo "   ABORT: Vault is SEALED. Unseal it first (see unseal-vault.sh), then re-run."
  exit 1
fi
echo "   unsealed"

echo
echo "=============================================================="
echo " 1. authenticate"
echo "=============================================================="
if [ -n "${VAULT_TOKEN:-}" ]; then
  echo "   using VAULT_TOKEN from the environment"
else
  echo "   Paste a token with rights to write secret/platform/observability and"
  echo "   create tokens. The initial root token is in"
  echo "   vault-keys-personal-stack.txt in this repo (untracked)."
  printf '   token: '
  read -rs VAULT_TOKEN; echo
  export VAULT_TOKEN
fi
if ! vault token lookup >/dev/null 2>&1; then
  echo "   ABORT: token rejected by Vault"; exit 1
fi
echo "   accepted"

if [ "${SKIP_WEBHOOK:-0}" != "1" ]; then
  echo
  echo "=============================================================="
  echo " 2. Discord webhook"
  echo "=============================================================="
  echo "   ROTATE FIRST: the previous URL was pasted into a chat transcript, so"
  echo "   treat it as burned. Delete that webhook in Discord (Server Settings"
  echo "   -> Integrations -> Webhooks) and create a new one."
  printf '   new webhook URL (blank to skip): '
  read -rs WEBHOOK; echo
  if [ -z "$WEBHOOK" ]; then
    echo "   skipped"
  else
    case "$WEBHOOK" in
      https://discord.com/api/webhooks/*|https://discordapp.com/api/webhooks/*) ;;
      *) echo "   ABORT: that does not look like a Discord webhook URL"; exit 1 ;;
    esac
    vault kv patch secret/platform/observability "discord.alert_webhook_url=$WEBHOOK" >/dev/null \
      && echo "   written to secret/platform/observability" \
      || { echo "   FAILED to write"; exit 1; }
    unset WEBHOOK
  fi
fi

if [ "${SKIP_TOKEN:-0}" != "1" ]; then
  echo
  echo "=============================================================="
  echo " 3. Vault metrics token"
  echo "=============================================================="
  if vault policy read prometheus-metrics >/dev/null 2>&1; then
    echo "   policy prometheus-metrics exists"
  else
    echo "   policy prometheus-metrics is MISSING -- creating it read-only on"
    echo "   the metrics endpoint only."
    vault policy write prometheus-metrics - >/dev/null <<'POLICY'
path "sys/metrics" {
  capabilities = ["read", "list"]
}
POLICY
    echo "   created"
  fi
  # -period makes this a periodic token: it renews indefinitely only if
  # something renews it, and nothing does, so it dies every 30 days. That is
  # what expired last time. VaultMetricsUnreachable now alerts when it happens.
  NEW_TOKEN=$(vault token create -policy=prometheus-metrics -period=720h -format=json \
    | sed -n 's/.*"client_token": *"\([^"]*\)".*/\1/p')
  if [ -z "$NEW_TOKEN" ]; then echo "   FAILED to create a token"; exit 1; fi
  vault kv patch secret/platform/observability "vault.prometheus_token=$NEW_TOKEN" >/dev/null \
    && echo "   new token created and written" \
    || { echo "   FAILED to write the token"; exit 1; }
  unset NEW_TOKEN

  echo "   forcing VSO to re-sync (it refreshes hourly otherwise)"
  k delete secret -n data-system vault-prometheus-token >/dev/null 2>&1 || true
  for _ in $(seq 1 30); do
    k get secret -n data-system vault-prometheus-token >/dev/null 2>&1 && break
    sleep 2
  done
  k get secret -n data-system vault-prometheus-token >/dev/null 2>&1 \
    && echo "   VSO re-created the secret" \
    || echo "   WARNING: VSO has not re-created it yet; check the VaultStaticSecret"
fi

if [ "$SUSPEND_CRONJOBS" = "1" ]; then
  echo
  echo "=============================================================="
  echo " 4. suspend the agents CronJobs"
  echo "=============================================================="
  echo "   These fail because the agent runtime's Claude OAuth token is revoked."
  echo "   Suspending stops the 6-hourly failures, and stops KubeJobFailed"
  echo "   paging Discord forever once delivery works. It also means Claude-backed"
  echo "   agent sessions and KB curation stay broken -- this hides the signal,"
  echo "   it does not fix the cause."
  for cj in agents-refresh-ping agents-kb-curator-triage agents-kb-curator-weekly agents-kb-install; do
    k patch cronjob -n agents-system "$cj" -p '{"spec":{"suspend":true}}' >/dev/null 2>&1 \
      && echo "   suspended $cj" || echo "   could not suspend $cj"
  done
  echo "   Note: apps-agents is Flux-managed, so a reconcile will unsuspend these."
  echo "   To make it stick, the CronJobs need removing in fleet-infra."
fi

echo
echo "=============================================================="
echo " 5. verify"
echo "=============================================================="
LEN=$(k get secret -n observability alertmanager-discord -o jsonpath='{.data.webhook-url}' 2>/dev/null | base64 -d 2>/dev/null | wc -c | tr -d ' ')
echo "   projected webhook-url length: ${LEN:-0} (0 means Alertmanager still has no destination)"

k port-forward -n observability svc/metrics-stack-prometheus "${PROM_LOCAL_PORT}:9090" >/dev/null 2>&1 &
PF_PIDS="$PF_PIDS $!"
if curl -fsS --retry 20 --retry-connrefused --retry-delay 1 "http://localhost:${PROM_LOCAL_PORT}/-/healthy" >/dev/null 2>&1; then
  DOWN=$(curl -s --get "http://localhost:${PROM_LOCAL_PORT}/api/v1/query" \
    --data-urlencode 'query=up{job="vault"} == 0' \
    | grep -o '"metric"' | wc -l | tr -d ' ')
  echo "   vault scrape targets still down: ${DOWN:-?} (0 means the metrics token works"
  echo "   and VaultSealed can evaluate again; Prometheus may need a scrape interval)"
else
  echo "   could not reach Prometheus to verify the scrape"
fi

echo
echo "Remaining, deliberately not automated:"
echo "  - Claude OAuth re-auth via the agents-login portal (browser flow)."
echo "  - Both tokens above are static. Kubernetes auth is the durable fix for"
echo "    the metrics token and for vault-raft-snapshot, which rots the same way."
