#!/usr/bin/env bash
# common.sh — Shared functions for repair-server.sh and rotate-credentials.sh.
#
# Source this file to get:
#   - Formatting helpers (step, ok, info, warn, die, spinner, mask, clear_line)
#   - vault_exec()       — run vault commands inside the Vault container
#   - update_secret()    — replace a Docker Swarm secret (legacy, non-versioned)
#   - wait_for_rabbitmq() / wait_for_services() — polling helpers
#   - prepare_auth_issuer_ca_file() / reapply_vault_oidc_config() — OIDC helpers
#
# Required globals (set by caller before sourcing):
#   STACK_DIR           — path to the personal-stack checkout (default: /opt/personal-stack)
#   LOG_FILE            — path to log file (default: ${STACK_DIR}/operation.log)

STACK_DIR="${STACK_DIR:-/opt/personal-stack}"
LOG_FILE="${LOG_FILE:-${STACK_DIR}/operation.log}"
VAULT_KEYS_FILE="${VAULT_KEYS_FILE:-${STACK_DIR}/.vault-keys}"
APP_SECRETS_FILE="${APP_SECRETS_FILE:-${STACK_DIR}/.vault-app-secrets}"
POLICIES_DIR="${POLICIES_DIR:-${STACK_DIR}/infra/vault/policies}"

# ── Formatting helpers ──────────────────────────────────────────────────────
BOLD='\033[1m'
DIM='\033[2m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
RESET='\033[0m'

STEP=0
TOTAL_STEPS="${TOTAL_STEPS:-1}"

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

clear_line() { printf "\r\033[K"; }
mask() { printf '%s****%s' "${1:0:3}" "${1: -2}"; }

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

# ── Vault helpers ──────────────────────────────────────────────────────────

find_vault_container() {
  VAULT_CONTAINER=$(docker ps --filter "name=personal-stack_vault" --format "{{.ID}}" | head -1)
  [[ -n "$VAULT_CONTAINER" ]] || return 1
}

vault_exec() {
  docker exec \
    -e VAULT_ADDR=http://127.0.0.1:8200 \
    -e VAULT_TOKEN="$VAULT_ROOT_TOKEN" \
    "$VAULT_CONTAINER" vault "$@"
}

load_vault_keys() {
  [[ -f "$VAULT_KEYS_FILE" ]] || die "${VAULT_KEYS_FILE} not found. Run init-vault.sh first."
  # shellcheck source=/dev/null
  source "$VAULT_KEYS_FILE"
  [[ -n "${VAULT_ROOT_TOKEN:-}" ]] || die "VAULT_ROOT_TOKEN not set in keys file."
}

load_app_secrets() {
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
}

ensure_vault_unsealed() {
  local seal_status
  seal_status=$(vault_exec status -format=json 2>/dev/null || echo '{"sealed":true}')
  if echo "$seal_status" | grep -q '"sealed":true'; then
    [[ -n "${VAULT_UNSEAL_KEY:-}" ]] || die "VAULT_UNSEAL_KEY not set in keys file."
    vault_exec operator unseal "$VAULT_UNSEAL_KEY" > /dev/null
    ok "Vault unsealed"
  else
    ok "Vault already unsealed"
  fi
}

# ── Docker secret helpers ──────────────────────────────────────────────────

update_secret() {
  local name="$1" value="$2"
  docker secret rm "$name" 2>/dev/null || true
  printf '%s' "$value" | docker secret create "$name" - > /dev/null
}

ensure_secret() {
  local name="$1" default="$2"
  if ! docker secret inspect "$name" > /dev/null 2>&1; then
    printf '%s' "$default" | docker secret create "$name" - > /dev/null
    info "Created missing secret: $name"
  fi
}

# ── Service wait helpers ───────────────────────────────────────────────────

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

wait_for_vault_api() {
  local timeout="${1:-90}" elapsed=0
  while [ "$elapsed" -lt "$timeout" ]; do
    VAULT_CONTAINER=$(docker ps --filter "name=personal-stack_vault" --format "{{.ID}}" | head -1)
    if [ -n "$VAULT_CONTAINER" ]; then
      if docker exec "$VAULT_CONTAINER" wget -qO- http://127.0.0.1:8200/v1/sys/seal-status > /dev/null 2>&1; then
        return 0
      fi
    fi
    printf "\r  ${DIM}Waiting for Vault API [%ds]${RESET}" "$elapsed"
    sleep 3
    elapsed=$((elapsed + 3))
  done
  clear_line
  return 1
}

# ── Stack deploy helper ────────────────────────────────────────────────────

deploy_stack() {
  local compose_file="${STACK_DIR}/docker-compose.prod.yml"
  local overlay_file="${STACK_DIR}/docker-compose.secrets.yml"

  if [[ -f "$overlay_file" ]]; then
    docker stack deploy \
      -c "$compose_file" \
      -c "$overlay_file" \
      personal-stack \
      --with-registry-auth > /dev/null 2>&1
  else
    docker stack deploy \
      -c "$compose_file" \
      personal-stack \
      --with-registry-auth > /dev/null 2>&1
  fi
}

# Rolling redeploy of a specific service (zero-downtime).
rolling_redeploy_service() {
  local service_name="$1"
  docker service update --force "personal-stack_${service_name}" > /dev/null 2>&1
}

# ── OIDC helpers ───────────────────────────────────────────────────────────

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

  # Read the Vault OIDC client secret from Vault KV if available
  local vault_oidc_secret
  vault_oidc_secret=$(vault_exec kv get -field="auth.clients.vault.secret" secret/auth-api 2>/dev/null) || true
  if [[ -n "$vault_oidc_secret" ]]; then
    VAULT_OIDC_CLIENT_SECRET="$vault_oidc_secret"
  fi

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

# ── Notification helper ────────────────────────────────────────────────────

notify_result() {
  local status="$1" summary="$2"
  if [[ -n "${NOTIFY_WEBHOOK:-}" ]]; then
    curl -sf -X POST "$NOTIFY_WEBHOOK" \
      -H 'Content-Type: application/json' \
      -d "{\"status\":\"${status}\",\"summary\":\"${summary}\",\"timestamp\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"}" \
      || warn "Failed to send notification"
  fi
}
