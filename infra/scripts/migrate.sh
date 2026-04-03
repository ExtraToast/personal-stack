#!/usr/bin/env bash
# migrate.sh — One-time migration from Docker Swarm to Nomad.
#
# This script contains all Swarm-specific logic: reading secrets from running
# containers, backing up Swarm state, cutting over traffic, and rolling back.
# After migration is complete this script can be removed.
#
# Usage:
#   migrate.sh <command> [--dry-run]
#
# Commands:
#   sync-secrets   Read Swarm container secrets and write to Vault KV
#   backup         Backup Swarm Vault raft, PostgreSQL dump, Traefik ACME
#   cutover        Scale down Swarm services so Nomad takes over
#   rollback       Stop Nomad edge/app jobs, scale Swarm back up
#   full           Full migration: backup -> sync -> setup -> deploy -> cutover
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STACK_DIR="${STACK_DIR:-/opt/personal-stack}"
BOOTSTRAP_ENV_FILE="${BOOTSTRAP_ENV_FILE:-${STACK_DIR}/.nomad-bootstrap.env}"
VAULT_KEYS_FILE="${VAULT_KEYS_FILE:-${STACK_DIR}/.vault-keys}"
NOMAD_KEYS_FILE="${NOMAD_KEYS_FILE:-${STACK_DIR}/.nomad-keys}"
BACKUP_DIR="${BACKUP_DIR:-${ROOT_DIR}/tmp/nomad-migration}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
IMAGE_REPO="${IMAGE_REPO:-ghcr.io/extratoast/personal-stack}"
VAULT_ADDR_DEFAULT="${VAULT_ADDR_DEFAULT:-http://127.0.0.1:8200}"
STACK_PREFIX="${STACK_PREFIX:-}"
MODE="apply"

# ── Helpers ────────────────────────────────────────────────────────────────

usage() {
  cat <<'EOF'
Usage: migrate.sh <command> [--dry-run]

Commands:
  sync-secrets   Read Swarm container secrets and write to Vault KV
  backup         Backup Swarm Vault raft, PostgreSQL dump, Traefik ACME
  cutover        Scale down Swarm services so Nomad takes over
  rollback       Stop Nomad edge/app jobs, scale Swarm back up
  full           Full migration: backup -> sync -> setup -> deploy -> cutover
EOF
}

run() {
  echo "+ $*"
  [[ "${MODE}" == "apply" ]] && "$@"
}

shell_single_quote() {
  printf "'%s'" "${1//\'/\'\"\'\"\'}"
}

load_vault_context() {
  if [[ -f "${BOOTSTRAP_ENV_FILE}" ]]; then
    set -a; source "${BOOTSTRAP_ENV_FILE}"; set +a
  fi
  export VAULT_ADDR="${VAULT_ADDR:-${VAULT_ADDR_DEFAULT}}"
  if [[ -z "${VAULT_TOKEN:-}" && -f "${VAULT_KEYS_FILE}" ]]; then
    source "${VAULT_KEYS_FILE}"
    export VAULT_TOKEN="${VAULT_ROOT_TOKEN:-}"
  fi
}

load_nomad_context() {
  export NOMAD_ADDR="${NOMAD_ADDR:-http://127.0.0.1:4646}"
  if [[ -z "${NOMAD_TOKEN:-}" && -f "${NOMAD_KEYS_FILE}" ]]; then
    source "${NOMAD_KEYS_FILE}"
    export NOMAD_TOKEN="${NOMAD_BOOTSTRAP_TOKEN:-}"
  fi
}

vault_is_unsealed() {
  curl -fsS "${VAULT_ADDR}/v1/sys/seal-status" | jq -e '.sealed == false' >/dev/null 2>&1
}

ensure_vault_unsealed() {
  if ! vault_is_unsealed; then
    if [[ -z "${VAULT_UNSEAL_KEY:-}" && -f "${VAULT_KEYS_FILE}" ]]; then
      source "${VAULT_KEYS_FILE}"
    fi
    if [[ -n "${VAULT_UNSEAL_KEY:-}" ]]; then
      vault operator unseal "${VAULT_UNSEAL_KEY}" >/dev/null
    else
      echo "Vault is sealed and no unseal key is available." >&2; exit 1
    fi
  fi
}

ensure_bootstrap_env_line() {
  local key="$1" value="$2" quoted
  quoted="$(shell_single_quote "${value}")"
  mkdir -p "$(dirname "${BOOTSTRAP_ENV_FILE}")"
  touch "${BOOTSTRAP_ENV_FILE}"
  chmod 600 "${BOOTSTRAP_ENV_FILE}"
  if grep -q "^${key}=" "${BOOTSTRAP_ENV_FILE}"; then
    sed -i "s|^${key}=.*|${key}=${quoted}|" "${BOOTSTRAP_ENV_FILE}"
  else
    printf '%s=%s\n' "${key}" "${quoted}" >>"${BOOTSTRAP_ENV_FILE}"
  fi
}

random_secret() { openssl rand -hex 32; }

# ── Swarm helpers ──────────────────────────────────────────────────────────

detect_stack_prefix_from_containers() {
  local names
  names="$(docker ps --format '{{.Names}}')"
  if printf '%s\n' "${names}" | grep -Eq '^personal-stack_'; then echo "personal-stack"; return; fi
  if printf '%s\n' "${names}" | grep -Eq '^private-stack_';  then echo "private-stack";  return; fi
  echo "Could not detect a running Swarm stack prefix." >&2; exit 1
}

detect_stack_prefix_from_services() {
  local services
  services="$(docker service ls --format '{{.Name}}')"
  if printf '%s\n' "${services}" | grep -Eq '^personal-stack_'; then echo "personal-stack"; return; fi
  if printf '%s\n' "${services}" | grep -Eq '^private-stack_';  then echo "private-stack";  return; fi
  echo "Could not detect a deployed Swarm stack prefix." >&2; exit 1
}

container_for_service() {
  docker ps --format '{{.Names}}' | grep -E "^${STACK_PREFIX}_${1}(\\.|$)" | head -n1
}

find_container() {
  local container
  container="$(container_for_service "$1")"
  [[ -n "${container}" ]] || { echo "No running container for '$1'." >&2; exit 1; }
  printf '%s' "${container}"
}

read_secret_or_default() {
  local service="$1" path="$2" fallback="${3:-}" container value=""
  container="$(container_for_service "${service}")"
  [[ -n "${container}" ]] && value="$(docker exec "${container}" sh -lc "cat '${path}'" 2>/dev/null || true)"
  if [[ -n "${value}" ]]; then printf '%s' "${value}"; else printf '%s' "${fallback}"; fi
}

vault_field_or_default() {
  local value
  value="$(vault kv get -field="$2" "$1" 2>/dev/null || true)"
  if [[ -n "${value}" ]]; then printf '%s' "${value}"; else printf '%s' "${3:-}"; fi
}

upsert_kv() {
  local path="$1"; shift
  vault kv patch "${path}" "$@" >/dev/null 2>&1 || vault kv put "${path}" "$@" >/dev/null
}

scale_service() {
  run docker service scale "${STACK_PREFIX}_${1}=${2}"
}

# ── sync-secrets command ───────────────────────────────────────────────────

sync_secrets_command() {
  load_vault_context
  ensure_vault_unsealed
  : "${VAULT_ADDR:?Set VAULT_ADDR}"
  : "${VAULT_TOKEN:?Set VAULT_TOKEN}"
  export VAULT_ADDR VAULT_TOKEN

  STACK_PREFIX="${STACK_PREFIX:-$(detect_stack_prefix_from_containers)}"
  echo "Detected Swarm stack prefix: ${STACK_PREFIX}"

  if [[ "${MODE}" == "dry-run" ]]; then
    echo "+ read secrets from Swarm containers -> patch Vault KV"; return
  fi

  local postgres_user postgres_password auth_db_user auth_db_password
  local assistant_db_user assistant_db_password n8n_db_user n8n_db_password
  local grafana_admin_user grafana_admin_password rabbitmq_user rabbitmq_password
  local cf_dns_api_token stalwart_admin_user stalwart_admin_password
  local n8n_oauth_secret grafana_oauth_secret vault_oauth_secret

  postgres_user="$(read_secret_or_default postgres /run/secrets/postgres_user postgres)"
  postgres_password="$(read_secret_or_default postgres /run/secrets/postgres_password)"
  auth_db_user="$(read_secret_or_default postgres /run/secrets/auth_db_user auth_user)"
  auth_db_password="$(read_secret_or_default postgres /run/secrets/auth_db_password auth_password)"
  assistant_db_user="$(read_secret_or_default postgres /run/secrets/assistant_db_user assistant_user)"
  assistant_db_password="$(read_secret_or_default postgres /run/secrets/assistant_db_password assistant_password)"
  n8n_db_user="$(read_secret_or_default n8n /run/secrets/n8n_db_user n8n_user)"
  n8n_db_password="$(read_secret_or_default n8n /run/secrets/n8n_db_password n8n_password)"
  grafana_admin_user="$(read_secret_or_default grafana /run/secrets/grafana_admin_user admin)"
  grafana_admin_password="$(read_secret_or_default grafana /run/secrets/grafana_admin_password)"
  rabbitmq_user="$(read_secret_or_default rabbitmq /run/secrets/rabbitmq_user guest)"
  rabbitmq_password="$(read_secret_or_default rabbitmq /run/secrets/rabbitmq_password)"

  cf_dns_api_token="$(read_secret_or_default traefik /run/secrets/cf_dns_api_token)"
  [[ -n "${cf_dns_api_token}" ]] || { echo "Could not read cf_dns_api_token from Swarm." >&2; exit 1; }

  n8n_oauth_secret="$(vault_field_or_default secret/auth-api "auth.clients.n8n.secret")"
  [[ -n "${n8n_oauth_secret}" ]] || n8n_oauth_secret="$(random_secret)"
  grafana_oauth_secret="$(vault_field_or_default secret/auth-api "auth.clients.grafana.secret")"
  [[ -n "${grafana_oauth_secret}" ]] || grafana_oauth_secret="$(random_secret)"
  vault_oauth_secret="$(vault_field_or_default secret/auth-api "auth.clients.vault.secret")"
  [[ -n "${vault_oauth_secret}" ]] || vault_oauth_secret="$(random_secret)"
  stalwart_admin_user="$(read_secret_or_default stalwart /run/secrets/stalwart_admin_user admin)"
  stalwart_admin_password="$(read_secret_or_default stalwart /run/secrets/stalwart_admin_password)"
  [[ -n "${stalwart_admin_password}" ]] || stalwart_admin_password="$(random_secret)"
  local stalwart_mail_password
  stalwart_mail_password="$(vault_field_or_default secret/auth-api "mail.password")"
  [[ -n "${stalwart_mail_password}" ]] || stalwart_mail_password="$(random_secret)"

  # Export for write_all_secrets_to_vault (sourced from setup.sh pattern)
  POSTGRES_USER="${postgres_user}"
  POSTGRES_PASSWORD="${postgres_password}"
  AUTH_DB_USER="${auth_db_user}"
  AUTH_DB_PASSWORD="${auth_db_password}"
  ASSISTANT_DB_USER="${assistant_db_user}"
  ASSISTANT_DB_PASSWORD="${assistant_db_password}"
  N8N_DB_USER="${n8n_db_user}"
  N8N_DB_PASSWORD="${n8n_db_password}"
  RABBITMQ_USER="${rabbitmq_user}"
  RABBITMQ_PASSWORD="${rabbitmq_password}"
  CF_DNS_API_TOKEN="${cf_dns_api_token}"
  N8N_OAUTH_CLIENT_SECRET="${n8n_oauth_secret}"
  GRAFANA_OAUTH_CLIENT_SECRET="${grafana_oauth_secret}"
  GRAFANA_ADMIN_USER="${grafana_admin_user}"
  GRAFANA_ADMIN_PASSWORD="${grafana_admin_password}"
  VAULT_OIDC_CLIENT_SECRET="${vault_oauth_secret}"
  STALWART_ADMIN_USER="${stalwart_admin_user}"
  STALWART_ADMIN_PASSWORD="${stalwart_admin_password}"
  STALWART_MAIL_PASSWORD="${stalwart_mail_password}"

  # Persist to bootstrap env
  for var in POSTGRES_USER POSTGRES_PASSWORD AUTH_DB_USER AUTH_DB_PASSWORD \
    ASSISTANT_DB_USER ASSISTANT_DB_PASSWORD N8N_DB_USER N8N_DB_PASSWORD \
    RABBITMQ_USER RABBITMQ_PASSWORD CF_DNS_API_TOKEN \
    GRAFANA_ADMIN_USER GRAFANA_ADMIN_PASSWORD STALWART_ADMIN_USER STALWART_ADMIN_PASSWORD \
    STALWART_MAIL_PASSWORD \
    N8N_OAUTH_CLIENT_SECRET GRAFANA_OAUTH_CLIENT_SECRET VAULT_OIDC_CLIENT_SECRET; do
    ensure_bootstrap_env_line "${var}" "${!var}"
  done

  # Write to Vault KV
  upsert_kv secret/auth-api \
    "auth.clients.grafana.secret=${GRAFANA_OAUTH_CLIENT_SECRET}" \
    "auth.clients.n8n.secret=${N8N_OAUTH_CLIENT_SECRET}" \
    "auth.clients.vault.secret=${VAULT_OIDC_CLIENT_SECRET}" \
    "mail.username=auth" \
    "mail.password=${STALWART_MAIL_PASSWORD}"

  vault kv put secret/platform/postgres \
    "postgres.user=${POSTGRES_USER}" "postgres.password=${POSTGRES_PASSWORD}" \
    "auth.user=${AUTH_DB_USER}" "auth.password=${AUTH_DB_PASSWORD}" \
    "assistant.user=${ASSISTANT_DB_USER}" "assistant.password=${ASSISTANT_DB_PASSWORD}" \
    "n8n.user=${N8N_DB_USER}" "n8n.password=${N8N_DB_PASSWORD}" >/dev/null

  vault kv put secret/platform/rabbitmq \
    "rabbitmq.user=${RABBITMQ_USER}" "rabbitmq.password=${RABBITMQ_PASSWORD}" >/dev/null

  vault kv put secret/platform/edge \
    "cloudflare.dns_api_token=${CF_DNS_API_TOKEN}" >/dev/null

  vault kv put secret/platform/automation \
    "n8n.oauth_client_secret=${N8N_OAUTH_CLIENT_SECRET}" >/dev/null

  vault kv put secret/platform/observability \
    "grafana.admin_user=${GRAFANA_ADMIN_USER}" "grafana.admin_password=${GRAFANA_ADMIN_PASSWORD}" \
    "grafana.oauth_client_secret=${GRAFANA_OAUTH_CLIENT_SECRET}" >/dev/null

  vault kv put secret/platform/mail \
    "stalwart.admin_user=${STALWART_ADMIN_USER}" "stalwart.admin_password=${STALWART_ADMIN_PASSWORD}" >/dev/null

  echo "Swarm secrets synced into Vault KV."
}

# ── backup command ─────────────────────────────────────────────────────────

backup_command() {
  load_vault_context
  STACK_PREFIX="${STACK_PREFIX:-$(detect_stack_prefix_from_containers)}"

  run mkdir -p "${BACKUP_DIR}"

  # Vault raft snapshot (best-effort: secrets are migrated via sync_secrets_command).
  # The Swarm Vault container has its own root token, stored in .vault-keys.swarm-backup.
  local vault_container
  vault_container="$(container_for_service vault)" || true
  if [[ -n "${vault_container}" ]]; then
    echo "+ Vault raft snapshot (best-effort)"
    if [[ "${MODE}" == "apply" ]]; then
      local swarm_token=""
      # Prefer the dedicated swarm backup keys file
      if [[ -f "${STACK_DIR}/.vault-keys.swarm-backup" ]]; then
        swarm_token="$(grep '^VAULT_ROOT_TOKEN=' "${STACK_DIR}/.vault-keys.swarm-backup" | head -1 | cut -d= -f2- | tr -d "'")"
      fi
      # Fall back to the current .vault-keys (may still hold the original Swarm token)
      [[ -n "${swarm_token}" ]] || swarm_token="${VAULT_TOKEN:-}"

      if [[ -z "${swarm_token}" ]]; then
        echo "  WARNING: No Vault token available for Swarm snapshot. Continuing."
      elif docker exec -e VAULT_ADDR=http://127.0.0.1:8200 -e VAULT_TOKEN="${swarm_token}" \
          "${vault_container}" vault operator raft snapshot save /tmp/swarm-vault.snap 2>&1 \
        && docker cp "${vault_container}:/tmp/swarm-vault.snap" "${BACKUP_DIR}/vault.snap" 2>/dev/null \
        && [[ -s "${BACKUP_DIR}/vault.snap" ]]; then
        echo "  Vault snapshot saved."
      else
        echo "  WARNING: Vault snapshot failed (Swarm Vault may be sealed or use a different token). Continuing."
      fi
    fi
  else
    echo "  No Swarm Vault container found, skipping snapshot."
  fi

  # PostgreSQL dump
  local pg_container
  pg_container="$(find_container postgres)"
  echo "+ PostgreSQL dump"
  if [[ "${MODE}" == "apply" ]]; then
    docker exec "${pg_container}" pg_dumpall -U postgres > "${BACKUP_DIR}/postgres.sql"
    [[ -s "${BACKUP_DIR}/postgres.sql" ]] || { echo "PostgreSQL backup empty." >&2; exit 1; }
  fi

  # Traefik ACME certificates
  local traefik_container
  traefik_container="$(find_container traefik)"
  echo "+ Traefik ACME backup"
  if [[ "${MODE}" == "apply" ]]; then
    docker cp "${traefik_container}:/letsencrypt/acme.json" "${BACKUP_DIR}/acme.json"
    [[ -s "${BACKUP_DIR}/acme.json" ]] || { echo "Traefik ACME backup empty." >&2; exit 1; }
  fi

  echo "Backups written to ${BACKUP_DIR}/"
}

# ── cutover command ────────────────────────────────────────────────────────

cutover_command() {
  STACK_PREFIX="${STACK_PREFIX:-$(detect_stack_prefix_from_services)}"
  echo "Scaling down Swarm stack: ${STACK_PREFIX}"

  scale_service traefik 0
  scale_service app-ui 0
  scale_service auth-ui 0
  scale_service assistant-ui 0
  scale_service auth-api 0
  scale_service assistant-api 0

  echo "Swarm services scaled down. Nomad now serves traffic."
}

# ── rollback command ───────────────────────────────────────────────────────

rollback_command() {
  load_nomad_context

  echo "Stopping all Nomad jobs..."
  for job in traefik stalwart app-ui auth-ui assistant-ui auth-api assistant-api \
             postgres valkey rabbitmq \
             grafana prometheus loki tempo promtail n8n uptime-kuma; do
    nomad job stop -purge "${job}" 2>/dev/null || true
  done

  STACK_PREFIX="${STACK_PREFIX:-$(detect_stack_prefix_from_services)}"
  echo "Scaling Swarm back up: ${STACK_PREFIX}"

  scale_service traefik 1
  scale_service app-ui 2
  scale_service auth-ui 2
  scale_service assistant-ui 2
  scale_service auth-api 2
  scale_service assistant-api 2

  echo "Rollback complete. Swarm is serving traffic again."
}

# ── full command ───────────────────────────────────────────────────────────

full_command() {
  local setup_script="${ROOT_DIR}/infra/scripts/setup.sh"
  local deploy_script="${ROOT_DIR}/infra/scripts/deploy.sh"

  backup_command
  sync_secrets_command

  # First pass: configure JWT auth, policies, roles, transit key.
  # Database engine and OIDC are skipped because postgres isn't running yet.
  sudo bash "${setup_script}" prepare-vault

  bash "${deploy_script}" --phase data --wait

  # Second pass: now that postgres is running, the database secrets engine
  # and OIDC auth (which needs auth-api) can be configured.
  sudo bash "${setup_script}" prepare-vault

  # Deploy everything except Traefik (Swarm still holds ports 80/443).
  IMAGE_TAG="${IMAGE_TAG}" IMAGE_REPO="${IMAGE_REPO}" \
    bash "${deploy_script}" --phase infra
  IMAGE_TAG="${IMAGE_TAG}" IMAGE_REPO="${IMAGE_REPO}" \
    bash "${deploy_script}" --phase apps --wait

  # Cut over: scale down Swarm services, freeing ports 80/443.
  cutover_command

  # Now Traefik can bind its ports.
  bash "${deploy_script}" --phase edge

  echo "Migration complete."
}

# ── Main ───────────────────────────────────────────────────────────────────

main() {
  local command="${1:-}"
  [[ -n "${command}" ]] || { usage; exit 1; }
  shift || true

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --dry-run) MODE="dry-run" ;;
      *)         echo "Unknown argument: $1" >&2; usage; exit 1 ;;
    esac
    shift
  done

  case "${command}" in
    sync-secrets) sync_secrets_command ;;
    backup)       backup_command ;;
    cutover)      cutover_command ;;
    rollback)     rollback_command ;;
    full)         full_command ;;
    *)            echo "Unknown command: ${command}" >&2; usage; exit 1 ;;
  esac
}

main "$@"
