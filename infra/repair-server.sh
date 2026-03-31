#!/usr/bin/env bash
# repair-server.sh — single script to fully repair the instance.
# Run on the server as root or a user with docker access.
#
# What it does (in order):
#   1. Unseals Vault if sealed
#   2. Rotates RabbitMQ credentials if user is "guest" (clears data volume)
#   3. Re-applies Vault policies
#   4. Re-applies Vault OIDC configuration
#   5. Syncs RabbitMQ + DB credentials to Vault KV
#   6. Regenerates AppRole credentials + updates Docker Swarm secrets
#   7. Redeploys the stack
#   8. Unseals Vault after redeploy
#   9. Waits for services to become healthy
set -euo pipefail

STACK_DIR="/opt/personal-stack"
LOG_FILE="${STACK_DIR}/repair-server.log"
TOTAL_STEPS=11

# Source shared library
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="${SCRIPT_DIR}/scripts/lib"
if [[ -f "${LIB_DIR}/common.sh" ]]; then
  source "${LIB_DIR}/common.sh"
else
  # Fallback: try relative to STACK_DIR (when running on server)
  source "${STACK_DIR}/infra/scripts/lib/common.sh"
fi

trap 'echo "[$(date "+%Y-%m-%d %H:%M:%S")] ERR: repair-server.sh failed at line ${LINENO}" >> "$LOG_FILE"' ERR

# ── Banner ──────────────────────────────────────────────────────────────────
printf "\n${BOLD}"
printf "  +-----------------------------------------+\n"
printf "  |        personal-stack repair             |\n"
printf "  +-----------------------------------------+${RESET}\n\n"

echo "[$(date '+%Y-%m-%d %H:%M:%S')] repair-server.sh started" >> "$LOG_FILE"

# ── Load credentials ────────────────────────────────────────────────────────
step "Loading credentials"

load_vault_keys
ok "Vault keys loaded"
load_app_secrets

# ── Locate containers ──────────────────────────────────────────────────────
step "Locating containers"

find_vault_container || die "Vault container not running. Is the stack up?"
ok "vault: ${VAULT_CONTAINER:0:12}"

# ── 1. Unseal Vault ────────────────────────────────────────────────────────
step "Unsealing Vault"
ensure_vault_unsealed

# ── 2. Check RabbitMQ credentials ──────────────────────────────────────────
step "Checking RabbitMQ credentials"

RMQ_CONTAINER=$(docker ps --filter "name=personal-stack_rabbitmq" --format "{{.ID}}" | head -1)
[ -n "$RMQ_CONTAINER" ] || die "RabbitMQ container not running."

CURRENT_RMQ_USER=$(docker exec "$RMQ_CONTAINER" cat /run/secrets/rabbitmq_user 2>/dev/null) \
  || die "Could not read rabbitmq_user secret."
CURRENT_RMQ_PASS=$(docker exec "$RMQ_CONTAINER" cat /run/secrets/rabbitmq_password 2>/dev/null) \
  || die "Could not read rabbitmq_password secret."

if [[ "$CURRENT_RMQ_USER" == "guest" ]]; then
  warn "User is 'guest' -- rotating (guest cannot connect remotely)"

  NEW_RMQ_USER="appuser"
  NEW_RMQ_PASS=$(openssl rand -base64 24 | tr -d '/+=' | head -c 32)

  info "Scaling down auth-api, assistant-api, rabbitmq..."
  docker service scale \
    personal-stack_auth-api=0 \
    personal-stack_assistant-api=0 \
    personal-stack_rabbitmq=0 > /dev/null 2>&1
  sleep 10

  info "Replacing Docker secrets..."
  docker secret rm rabbitmq_user rabbitmq_password 2>/dev/null || true
  printf '%s' "$NEW_RMQ_USER" | docker secret create rabbitmq_user - > /dev/null
  printf '%s' "$NEW_RMQ_PASS" | docker secret create rabbitmq_password - > /dev/null

  info "Clearing RabbitMQ data volume..."
  docker volume rm personal-stack_rabbitmq-data 2>/dev/null || true

  info "Redeploying stack..."
  deploy_stack

  wait_for_rabbitmq 120

  RMQ_USER="$NEW_RMQ_USER"
  RMQ_PASS="$NEW_RMQ_PASS"

  cat > "$APP_SECRETS_FILE" <<EOF
RABBITMQ_USER=${RMQ_USER}
RABBITMQ_PASSWORD=${RMQ_PASS}
AUTH_DB_USER=${AUTH_DB_USER}
AUTH_DB_PASSWORD=${AUTH_DB_PASSWORD}
ASSISTANT_DB_USER=${ASSISTANT_DB_USER}
ASSISTANT_DB_PASSWORD=${ASSISTANT_DB_PASSWORD}
STALWART_ADMIN_USER=${STALWART_ADMIN_USER:-admin}
STALWART_ADMIN_PASSWORD=${STALWART_ADMIN_PASSWORD:-}
EOF
  chmod 600 "$APP_SECRETS_FILE"
  ok "Credentials rotated to user '${NEW_RMQ_USER}'"

  find_vault_container || die "Vault container not running after redeploy."
else
  ok "User '${CURRENT_RMQ_USER}' -- no rotation needed"
  RMQ_USER="$CURRENT_RMQ_USER"
  RMQ_PASS="$CURRENT_RMQ_PASS"
fi

# ── 3. Re-apply Vault policies ─────────────────────────────────────────────
step "Applying Vault policies"

for policy in auth-api assistant-api; do
  pfile="${POLICIES_DIR}/${policy}.hcl"
  [[ -f "$pfile" ]] || die "Policy file not found: $pfile"
  docker exec -i \
    -e VAULT_ADDR=http://127.0.0.1:8200 \
    -e VAULT_TOKEN="$VAULT_ROOT_TOKEN" \
    "$VAULT_CONTAINER" vault policy write "$policy" - < "$pfile" > /dev/null
  ok "${policy}"
done

# ── 4. Re-apply Vault OIDC configuration (deferred until after services are up)
step "Re-applying Vault OIDC configuration"
info "Deferred until after services are healthy (auth issuer must be reachable)"
OIDC_DEFERRED=true

# ── 5. Sync credentials to Vault KV ────────────────────────────────────────
step "Syncing secrets to Vault KV"

# Preserve existing JWT signing key or generate a new one
EXISTING_SIGNING_KEY=$(vault_exec kv get -field="auth.signing-key" secret/auth-api 2>/dev/null || true)
if [[ -z "$EXISTING_SIGNING_KEY" ]]; then
  info "No JWT signing key found in Vault -- generating new RSA key"
  EXISTING_SIGNING_KEY=$(openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 2>/dev/null)
  ok "New JWT signing key generated"
else
  ok "Existing JWT signing key preserved"
fi

# Preserve existing OAuth2 client secrets or generate new ones
GRAFANA_SECRET=$(vault_exec kv get -field="auth.clients.grafana.secret" secret/auth-api 2>/dev/null || true)
N8N_SECRET=$(vault_exec kv get -field="auth.clients.n8n.secret" secret/auth-api 2>/dev/null || true)
VAULT_SECRET=$(vault_exec kv get -field="auth.clients.vault.secret" secret/auth-api 2>/dev/null || true)
STALWART_SECRET=$(vault_exec kv get -field="auth.clients.stalwart.secret" secret/auth-api 2>/dev/null || true)

[[ -n "$GRAFANA_SECRET" ]] || GRAFANA_SECRET=$(openssl rand -base64 32 | tr -d '/+=' | head -c 40)
[[ -n "$N8N_SECRET" ]] || N8N_SECRET=$(openssl rand -base64 32 | tr -d '/+=' | head -c 40)
[[ -n "$VAULT_SECRET" ]] || VAULT_SECRET=$(openssl rand -base64 32 | tr -d '/+=' | head -c 40)
[[ -n "$STALWART_SECRET" ]] || STALWART_SECRET=$(openssl rand -base64 32 | tr -d '/+=' | head -c 40)

vault_exec kv put secret/auth-api \
  "spring.rabbitmq.username=${RMQ_USER}" \
  "spring.rabbitmq.password=${RMQ_PASS}" \
  "auth.signing-key=${EXISTING_SIGNING_KEY}" \
  "auth.clients.grafana.secret=${GRAFANA_SECRET}" \
  "auth.clients.n8n.secret=${N8N_SECRET}" \
  "auth.clients.vault.secret=${VAULT_SECRET}" \
  "auth.clients.stalwart.secret=${STALWART_SECRET}" > /dev/null
ok "secret/auth-api (JWT signing key + RabbitMQ + OAuth2 client secrets)"

vault_exec kv put secret/assistant-api \
  "spring.rabbitmq.username=${RMQ_USER}" \
  "spring.rabbitmq.password=${RMQ_PASS}" > /dev/null
ok "secret/assistant-api"

info "Keys: spring.rabbitmq.username, spring.rabbitmq.password, auth.signing-key, auth.clients.*"

# ── 6. Regenerate AppRole credentials ──────────────────────────────────────
step "Regenerating AppRole credentials"

vault_exec auth enable approle 2>/dev/null || true

for role in auth-api assistant-api; do
  vault_exec write "auth/approle/role/${role}" \
    token_policies="${role}" \
    token_ttl=1h \
    token_max_ttl=4h \
    secret_id_ttl=0 > /dev/null
done
ok "AppRole roles configured"

AUTH_API_ROLE_ID=$(vault_exec read -field=role_id auth/approle/role/auth-api/role-id)
AUTH_API_SECRET_ID=$(vault_exec write -f -field=secret_id auth/approle/role/auth-api/secret-id)
ASSISTANT_API_ROLE_ID=$(vault_exec read -field=role_id auth/approle/role/assistant-api/role-id)
ASSISTANT_API_SECRET_ID=$(vault_exec write -f -field=secret_id auth/approle/role/assistant-api/secret-id)

ok "auth-api      role_id: $(mask "$AUTH_API_ROLE_ID")"
ok "assistant-api role_id: $(mask "$ASSISTANT_API_ROLE_ID")"

# ── Tear down stack, swap secrets, redeploy ─────────────────────────────────
info "Removing stack to release secrets..."
docker stack rm personal-stack > /dev/null 2>&1

elapsed=0
while [ "$elapsed" -lt 60 ]; do
  remaining=$(docker service ls --filter name=personal-stack_ -q 2>/dev/null | wc -l)
  [ "$remaining" -eq 0 ] && break
  printf "\r  ${DIM}Draining services [%ds]${RESET}" "$elapsed"
  sleep 2
  elapsed=$((elapsed + 2))
done
clear_line

elapsed=0
while [ "$elapsed" -lt 60 ]; do
  if ! docker network inspect personal-stack_personal-stack-overlay > /dev/null 2>&1; then
    break
  fi
  printf "\r  ${DIM}Waiting for network cleanup [%ds]${RESET}" "$elapsed"
  sleep 2
  elapsed=$((elapsed + 2))
done
clear_line
ok "Stack removed"

info "Replacing Vault secrets..."
update_secret vault_auth_api_role_id      "$AUTH_API_ROLE_ID"
update_secret vault_auth_api_secret_id    "$AUTH_API_SECRET_ID"
update_secret vault_assistant_api_role_id "$ASSISTANT_API_ROLE_ID"
update_secret vault_assistant_api_secret_id "$ASSISTANT_API_SECRET_ID"

# Sync OAuth2 client secrets to Swarm (for Grafana, n8n, Stalwart)
update_secret oauth2_grafana_secret   "$GRAFANA_SECRET"
update_secret oauth2_n8n_secret       "$N8N_SECRET"
update_secret oauth2_stalwart_secret  "$STALWART_SECRET"

# Ensure Stalwart admin secrets exist (created during provisioning, but may be
# missing on deployments that predate Stalwart OIDC integration)
: "${STALWART_ADMIN_USER:=admin}"
if [[ -z "${STALWART_ADMIN_PASSWORD:-}" ]]; then
  STALWART_ADMIN_PASSWORD=$(openssl rand -base64 24 | tr -d '/+=' | head -c 32)
  info "Generated new Stalwart admin password"
fi
ensure_secret stalwart_admin_user "$STALWART_ADMIN_USER"
ensure_secret stalwart_admin_password "$STALWART_ADMIN_PASSWORD"
ensure_secret cf_dns_api_token "${CF_DNS_API_TOKEN:-}"

# Persist Stalwart credentials to app-secrets file
if [[ -f "$APP_SECRETS_FILE" ]]; then
  if ! grep -q '^STALWART_ADMIN_USER=' "$APP_SECRETS_FILE" 2>/dev/null; then
    printf '\nSTALWART_ADMIN_USER=%s\nSTALWART_ADMIN_PASSWORD=%s\n' \
      "$STALWART_ADMIN_USER" "$STALWART_ADMIN_PASSWORD" >> "$APP_SECRETS_FILE"
    info "Stalwart credentials appended to ${APP_SECRETS_FILE}"
  fi
fi

ok "Docker Swarm secrets updated"

# ── 7. Redeploy stack ──────────────────────────────────────────────────────
step "Redeploying stack"
deploy_stack
ok "Stack deployed"

# ── 8. Unseal Vault after redeploy ─────────────────────────────────────────
step "Unsealing Vault after redeploy"

if wait_for_vault_api 90; then
  vault_exec operator unseal "$VAULT_UNSEAL_KEY" > /dev/null 2>&1 || true
  ok "Vault unsealed"
else
  die "Vault container did not start after redeploy."
fi

# ── 9. Wait for services ───────────────────────────────────────────────────
step "Waiting for services to become healthy"
wait_for_services 180

# ── 10. Apply deferred OIDC configuration ─────────────────────────────────
if [[ "${OIDC_DEFERRED:-false}" == "true" ]]; then
  step "Applying Vault OIDC configuration (deferred)"

  find_vault_container
  if [[ -n "$VAULT_CONTAINER" ]]; then
    if reapply_vault_oidc_config; then
      ok "Vault OIDC configuration repaired"
    else
      warn "OIDC configuration failed -- auth issuer may not be reachable yet. Re-run repair later."
    fi
  else
    warn "Vault container not found -- skipping OIDC configuration"
  fi
fi

# ── Final report ────────────────────────────────────────────────────────────
printf "\n${BOLD}  Service Status${RESET}\n"
printf "  %-30s  %s  %s\n" "${DIM}SERVICE${RESET}" "${DIM}RUNNING${RESET}" "${DIM}FAILED${RESET}"
printf "  %-30s  %s  %s\n" "------------------------------" "-------" "------"

for svc in personal-stack_auth-api personal-stack_assistant-api personal-stack_rabbitmq personal-stack_vault personal-stack_postgres personal-stack_stalwart; do
  running=$(docker service ps "$svc" --filter "desired-state=running" \
    --format "{{.CurrentState}}" 2>/dev/null | grep -c "^Running" || true)
  failed=$(docker service ps "$svc" --filter "desired-state=shutdown" \
    --format "{{.CurrentState}}" 2>/dev/null | grep -c "^Failed\|^Rejected" || true)
  short="${svc#personal-stack_}"
  if [ "$running" -ge 1 ]; then
    printf "  %-30s  ${GREEN}%d${RESET}        %d\n" "$short" "$running" "$failed"
  else
    printf "  %-30s  ${RED}%d${RESET}        %d\n" "$short" "$running" "$failed"
  fi
done

printf "\n${BOLD}${GREEN}  +-----------------------------------------+${RESET}\n"
printf "${BOLD}${GREEN}  |             Repair complete              |${RESET}\n"
printf "${BOLD}${GREEN}  +-----------------------------------------+${RESET}\n"
printf "  ${DIM}Full log: ${LOG_FILE}${RESET}\n\n"

echo "[$(date '+%Y-%m-%d %H:%M:%S')] repair-server.sh complete" >> "$LOG_FILE"
