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
VAULT_KEYS_FILE="${STACK_DIR}/.vault-keys"
APP_SECRETS_FILE="${STACK_DIR}/.vault-app-secrets"
POLICIES_DIR="${STACK_DIR}/infra/vault/policies"
LOG_FILE="${STACK_DIR}/repair-server.log"

# ── Formatting helpers ──────────────────────────────────────────────────────
BOLD='\033[1m'
DIM='\033[2m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
RESET='\033[0m'

STEP=0
TOTAL_STEPS=11

step() {
  STEP=$((STEP + 1))
  printf "\n${BOLD}${CYAN}[%d/%d]${RESET} ${BOLD}%s${RESET}\n" "$STEP" "$TOTAL_STEPS" "$*"
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] [${STEP}/${TOTAL_STEPS}] $*" >> "$LOG_FILE"
}

ok()   { printf "  ${GREEN}+${RESET} %s\n" "$*"; echo "[$(date '+%Y-%m-%d %H:%M:%S')]   OK: $*" >> "$LOG_FILE"; }
info() { printf "  ${DIM}%s${RESET}\n" "$*"; echo "[$(date '+%Y-%m-%d %H:%M:%S')]   $*" >> "$LOG_FILE"; }
warn() { printf "  ${YELLOW}!${RESET} %s\n" "$*"; echo "[$(date '+%Y-%m-%d %H:%M:%S')]   WARN: $*" >> "$LOG_FILE"; }

die() {
  printf "\n  ${RED}ERROR:${RESET} %s\n" "$*"
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] ERROR: $*" >> "$LOG_FILE"
  exit 1
}

spinner() {
  local pid=$1 label=$2
  local frames=('.' '..' '...' '....' '.....')
  local i=0
  while kill -0 "$pid" 2>/dev/null; do
    printf "\r  ${DIM}%s%s${RESET}" "$label" "${frames[i++ % ${#frames[@]}]}"
    sleep 0.4
  done
  printf "\r\033[K"
}

mask() { printf '%s****%s' "${1:0:3}" "${1: -2}"; }

wait_for_rabbitmq() {
  local timeout="$1" elapsed=0
  while [ "$elapsed" -lt "$timeout" ]; do
    local cid
    cid=$(docker ps --filter "name=personal-stack_rabbitmq" --format "{{.ID}}" | head -1)
    if [ -n "$cid" ] && docker exec "$cid" rabbitmq-diagnostics check_running 2>/dev/null; then
      ok "RabbitMQ ready (${elapsed}s)"
      return 0
    fi
    printf "\r  ${DIM}Waiting for RabbitMQ [%ds/%ds]${RESET}" "$elapsed" "$timeout"
    sleep 5
    elapsed=$((elapsed + 5))
  done
  printf "\r\033[K"
  die "RabbitMQ did not become ready within ${timeout}s."
}

wait_for_services() {
  local timeout="$1" elapsed=0
  while [ "$elapsed" -lt "$timeout" ]; do
    local auth_r assistant_r
    auth_r=$(docker service ps personal-stack_auth-api --filter "desired-state=running" \
      --format "{{.CurrentState}}" 2>/dev/null | grep -c "^Running" || true)
    assistant_r=$(docker service ps personal-stack_assistant-api --filter "desired-state=running" \
      --format "{{.CurrentState}}" 2>/dev/null | grep -c "^Running" || true)
    if [ "$auth_r" -ge 1 ] && [ "$assistant_r" -ge 1 ]; then
      printf "\r\033[K"
      ok "auth-api: ${auth_r} running, assistant-api: ${assistant_r} running (${elapsed}s)"
      return 0
    fi
    printf "\r  ${DIM}Waiting for services [%ds/%ds] auth-api:%d assistant-api:%d${RESET}" \
      "$elapsed" "$timeout" "$auth_r" "$assistant_r"
    sleep 10
    elapsed=$((elapsed + 10))
  done
  printf "\r\033[K"
  warn "Services may not be fully healthy after ${timeout}s"
}

# ── Banner ──────────────────────────────────────────────────────────────────
clear_line() { printf "\r\033[K"; }

printf "\n${BOLD}"
printf "  +-----------------------------------------+\n"
printf "  |        personal-stack repair             |\n"
printf "  +-----------------------------------------+${RESET}\n\n"

echo "[$(date '+%Y-%m-%d %H:%M:%S')] repair-server.sh started" >> "$LOG_FILE"

# ── Load credentials ────────────────────────────────────────────────────────
step "Loading credentials"

[[ -f "$VAULT_KEYS_FILE" ]] || die "${VAULT_KEYS_FILE} not found. Run init-vault.sh first."
# shellcheck source=/dev/null
source "$VAULT_KEYS_FILE"
[[ -n "${VAULT_ROOT_TOKEN:-}" ]] || die "VAULT_ROOT_TOKEN not set in keys file."
ok "Vault keys loaded"

if [[ -f "$APP_SECRETS_FILE" ]]; then
  # shellcheck source=/dev/null
  source "$APP_SECRETS_FILE"
  ok "Application secrets loaded"
else
  warn "${APP_SECRETS_FILE} not found -- using defaults"
fi

: "${AUTH_DB_USER:=auth_user}"
: "${AUTH_DB_PASSWORD:=auth_password}"
: "${ASSISTANT_DB_USER:=assistant_user}"
: "${ASSISTANT_DB_PASSWORD:=assistant_password}"
: "${AUTH_ISSUER:=https://auth.jorisjonkers.dev}"
: "${VAULT_PUBLIC_ADDR:=https://vault.jorisjonkers.dev}"
: "${VAULT_OIDC_CLIENT_SECRET:=vault-secret}"

# ── Locate containers ──────────────────────────────────────────────────────
step "Locating containers"

VAULT_CONTAINER=$(docker ps --filter "name=personal-stack_vault" --format "{{.ID}}" | head -1)
[ -n "$VAULT_CONTAINER" ] || die "Vault container not running. Is the stack up?"
ok "vault: ${VAULT_CONTAINER:0:12}"

vault_exec() {
  docker exec \
    -e VAULT_ADDR=http://127.0.0.1:8200 \
    -e VAULT_TOKEN="$VAULT_ROOT_TOKEN" \
    "$VAULT_CONTAINER" vault "$@"
}

json_array_from_csv() {
  local csv="$1"
  local raw=()
  local escaped=()
  local item
  IFS=',' read -r -a raw <<< "$csv"
  for item in "${raw[@]}"; do
    item="${item#"${item%%[![:space:]]*}"}"
    item="${item%"${item##*[![:space:]]}"}"
    [[ -n "$item" ]] || continue
    escaped+=("\"$item\"")
  done
  local joined=""
  local i
  for i in "${!escaped[@]}"; do
    [[ "$i" -gt 0 ]] && joined+=", "
    joined+="${escaped[$i]}"
  done
  printf '[%s]' "$joined"
}

extract_host_and_port() {
  local url="$1"
  local authority="${url#*://}"
  authority="${authority%%/*}"
  if [[ "$authority" == *:* ]]; then
    printf '%s %s\n' "${authority%%:*}" "${authority##*:}"
  else
    printf '%s 443\n' "$authority"
  fi
}

copy_into_vault_container() {
  local source_file="$1"
  local destination_file="$2"
  docker exec -i "$VAULT_CONTAINER" sh -c "cat > '$destination_file'" < "$source_file"
}

prepare_auth_issuer_ca_file() {
  [[ "$AUTH_ISSUER" == https://* ]] || return

  command -v openssl >/dev/null 2>&1 || die "openssl is required to fetch the auth issuer CA certificate chain."

  local issuer_host
  local issuer_port
  local ca_file
  read -r issuer_host issuer_port <<< "$(extract_host_and_port "$AUTH_ISSUER")"
  ca_file="$(mktemp)"
  openssl s_client -showcerts -servername "$issuer_host" -connect "$issuer_host:$issuer_port" </dev/null 2>/dev/null \
    | awk '/BEGIN CERTIFICATE/,/END CERTIFICATE/ { print }' > "$ca_file"

  if [[ ! -s "$ca_file" ]]; then
    rm -f "$ca_file"
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ERROR: Failed to fetch the auth issuer certificate chain from $AUTH_ISSUER." >> "$LOG_FILE"
    return 1
  fi
  printf '%s\n' "$ca_file"
}

reapply_vault_oidc_config() {
  local role_payload_file
  local ca_file=""
  local container_role_payload_file="/tmp/vault-oidc-role.json"
  local container_ca_file="/tmp/auth-issuer-ca.pem"
  local oidc_role_name="${VAULT_OIDC_ROLE_NAME:-default}"
  local oidc_client_id="${VAULT_OIDC_CLIENT_ID:-vault}"
  local oidc_scopes="${VAULT_OIDC_SCOPES:-openid,profile,email}"
  local bound_role_values="${VAULT_BOUND_ROLE_VALUES:-ROLE_ADMIN,SERVICE_VAULT}"
  local token_policies="${VAULT_TOKEN_POLICIES:-admin}"
  local allowed_redirect_uris="${VAULT_ALLOWED_REDIRECT_URIS:-${VAULT_PUBLIC_ADDR%/}/ui/vault/auth/oidc/oidc/callback,http://localhost:8250/oidc/callback}"
  local allowed_redirect_uris_json
  local oidc_scopes_json
  local bound_roles_json
  local token_policies_json

  role_payload_file="$(mktemp)"
  allowed_redirect_uris_json="$(json_array_from_csv "$allowed_redirect_uris")"
  oidc_scopes_json="$(json_array_from_csv "$oidc_scopes")"
  bound_roles_json="$(json_array_from_csv "$bound_role_values")"
  token_policies_json="$(json_array_from_csv "$token_policies")"

  cat > "$role_payload_file" <<EOF
{
  "bound_audiences": "${oidc_client_id}",
  "allowed_redirect_uris": ${allowed_redirect_uris_json},
  "user_claim": "sub",
  "groups_claim": "roles",
  "oidc_scopes": ${oidc_scopes_json},
  "bound_claims": {
    "roles": ${bound_roles_json}
  },
  "token_policies": ${token_policies_json}
}
EOF

  vault_exec auth enable oidc >> "$LOG_FILE" 2>&1 || true

  ca_file="$(prepare_auth_issuer_ca_file)" || true
  if [[ -n "$ca_file" && -s "$ca_file" ]]; then
    copy_into_vault_container "$ca_file" "$container_ca_file" \
      || die "Failed to copy issuer CA certificate into Vault container"
    vault_exec write auth/oidc/config \
      "oidc_discovery_url=${AUTH_ISSUER}" \
      "oidc_discovery_ca_pem=@${container_ca_file}" \
      "oidc_client_id=${oidc_client_id}" \
      "oidc_client_secret=${VAULT_OIDC_CLIENT_SECRET}" \
      "default_role=${oidc_role_name}" >> "$LOG_FILE" 2>&1 \
      || die "Failed to write OIDC config (check ${LOG_FILE} for details)"
    ok "OIDC auth config written (with CA certificate)"
  else
    vault_exec write auth/oidc/config \
      "oidc_discovery_url=${AUTH_ISSUER}" \
      "oidc_client_id=${oidc_client_id}" \
      "oidc_client_secret=${VAULT_OIDC_CLIENT_SECRET}" \
      "default_role=${oidc_role_name}" >> "$LOG_FILE" 2>&1 \
      || die "Failed to write OIDC config (check ${LOG_FILE} for details)"
    ok "OIDC auth config written"
  fi

  copy_into_vault_container "$role_payload_file" "$container_role_payload_file" \
    || die "Failed to copy OIDC role payload into Vault container"
  vault_exec write "auth/oidc/role/${oidc_role_name}" "@${container_role_payload_file}" >> "$LOG_FILE" 2>&1 \
    || die "Failed to write OIDC role '${oidc_role_name}' (check ${LOG_FILE} for details)"

  rm -f "$role_payload_file"
  [[ -n "$ca_file" ]] && rm -f "$ca_file" || true
}

# ── 1. Unseal Vault ────────────────────────────────────────────────────────
step "Unsealing Vault"

SEAL_STATUS=$(vault_exec status -format=json 2>/dev/null || echo '{"sealed":true}')
if echo "$SEAL_STATUS" | grep -q '"sealed":true'; then
  [[ -n "${VAULT_UNSEAL_KEY:-}" ]] || die "VAULT_UNSEAL_KEY not set in keys file."
  vault_exec operator unseal "$VAULT_UNSEAL_KEY" > /dev/null
  ok "Vault unsealed"
else
  ok "Vault already unsealed"
fi

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
  docker stack deploy \
    -c "${STACK_DIR}/docker-compose.prod.yml" \
    personal-stack \
    --with-registry-auth > /dev/null 2>&1

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

  VAULT_CONTAINER=$(docker ps --filter "name=personal-stack_vault" --format "{{.ID}}" | head -1)
  [ -n "$VAULT_CONTAINER" ] || die "Vault container not running after redeploy."
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

vault_exec kv put secret/auth-api \
  "spring.rabbitmq.username=${RMQ_USER}" \
  "spring.rabbitmq.password=${RMQ_PASS}" \
  "spring.datasource.username=${AUTH_DB_USER}" \
  "spring.datasource.password=${AUTH_DB_PASSWORD}" \
  "auth.signing-key=${EXISTING_SIGNING_KEY}" > /dev/null
ok "secret/auth-api (including JWT signing key)"

vault_exec kv put secret/assistant-api \
  "spring.rabbitmq.username=${RMQ_USER}" \
  "spring.rabbitmq.password=${RMQ_PASS}" \
  "spring.datasource.username=${ASSISTANT_DB_USER}" \
  "spring.datasource.password=${ASSISTANT_DB_PASSWORD}" > /dev/null
ok "secret/assistant-api"

info "Keys: spring.rabbitmq.username, spring.rabbitmq.password, spring.datasource.username, spring.datasource.password"

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
update_secret() {
  local name="$1" value="$2"
  docker secret rm "$name" 2>/dev/null || true
  printf '%s' "$value" | docker secret create "$name" - > /dev/null
}
update_secret vault_auth_api_role_id      "$AUTH_API_ROLE_ID"
update_secret vault_auth_api_secret_id    "$AUTH_API_SECRET_ID"
update_secret vault_assistant_api_role_id "$ASSISTANT_API_ROLE_ID"
update_secret vault_assistant_api_secret_id "$ASSISTANT_API_SECRET_ID"

# Ensure Stalwart admin secrets exist (created during provisioning, but may be
# missing on deployments that predate Stalwart OIDC integration)
ensure_secret() {
  local name="$1" default="$2"
  if ! docker secret inspect "$name" > /dev/null 2>&1; then
    printf '%s' "$default" | docker secret create "$name" - > /dev/null
    info "Created missing secret: $name"
  fi
}
: "${STALWART_ADMIN_USER:=admin}"
if [[ -z "${STALWART_ADMIN_PASSWORD:-}" ]]; then
  STALWART_ADMIN_PASSWORD=$(openssl rand -base64 24 | tr -d '/+=' | head -c 32)
  info "Generated new Stalwart admin password"
fi
ensure_secret stalwart_admin_user "$STALWART_ADMIN_USER"
ensure_secret stalwart_admin_password "$STALWART_ADMIN_PASSWORD"

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

docker stack deploy \
  -c "${STACK_DIR}/docker-compose.prod.yml" \
  personal-stack \
  --with-registry-auth > /dev/null 2>&1
ok "Stack deployed"

# ── 8. Unseal Vault after redeploy ─────────────────────────────────────────
step "Unsealing Vault after redeploy"

elapsed=0
while [ "$elapsed" -lt 90 ]; do
  VAULT_CONTAINER=$(docker ps --filter "name=personal-stack_vault" --format "{{.ID}}" | head -1)
  if [ -n "$VAULT_CONTAINER" ]; then
    if docker exec "$VAULT_CONTAINER" wget -qO- http://127.0.0.1:8200/v1/sys/seal-status > /dev/null 2>&1; then
      break
    fi
  fi
  printf "\r  ${DIM}Waiting for Vault API [%ds]${RESET}" "$elapsed"
  sleep 3
  elapsed=$((elapsed + 3))
done
clear_line
[ -n "$VAULT_CONTAINER" ] || die "Vault container did not start after redeploy."

vault_exec operator unseal "$VAULT_UNSEAL_KEY" > /dev/null 2>&1 || true
ok "Vault unsealed"

# ── 9. Wait for services ───────────────────────────────────────────────────
step "Waiting for services to become healthy"

wait_for_services 180

# ── 10. Apply deferred OIDC configuration ─────────────────────────────────
if [[ "${OIDC_DEFERRED:-false}" == "true" ]]; then
  step "Applying Vault OIDC configuration (deferred)"

  # Re-resolve Vault container after redeploy
  VAULT_CONTAINER=$(docker ps --filter "name=personal-stack_vault" --format "{{.ID}}" | head -1)
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

for svc in personal-stack_auth-api personal-stack_assistant-api personal-stack_rabbitmq personal-stack_vault personal-stack_postgres; do
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
