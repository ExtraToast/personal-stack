#!/usr/bin/env bash
# setup.sh — One-time server setup for the Nomad/Vault/Consul control plane.
#
# Idempotent: running twice skips already-completed steps.
#
# Usage:
#   setup.sh <command> [--dry-run]
#
# Commands:
#   install          Install Docker, Consul, Nomad, Vault packages
#   configure        Write configs, create volumes, set UFW rules, enable services
#   init-vault       Initialize and unseal Vault, enable KV engine
#   init-nomad       Bootstrap Nomad ACL
#   seed-secrets     Seed Vault KV from .nomad-bootstrap.env
#   prepare-vault    Configure JWT auth, policies, roles, transit, database engine, OIDC
#   rotate-secrets   Rotate PostgreSQL and RabbitMQ passwords
#   full             Run the complete bootstrap sequence including deploy
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STACK_DIR="${STACK_DIR:-/opt/personal-stack}"
NOMAD_DIR="${ROOT_DIR}/infra/nomad"
VAULT_DIR="${NOMAD_DIR}/vault"
MODE="apply"
BOOTSTRAP_ENV_FILE="${BOOTSTRAP_ENV_FILE:-${STACK_DIR}/.nomad-bootstrap.env}"
VAULT_KEYS_FILE="${VAULT_KEYS_FILE:-${STACK_DIR}/.vault-keys}"
NOMAD_KEYS_FILE="${NOMAD_KEYS_FILE:-${STACK_DIR}/.nomad-keys}"
NOMAD_ADDR="${NOMAD_ADDR:-http://127.0.0.1:4646}"
NOMAD_JWKS_URL="${NOMAD_JWKS_URL:-http://127.0.0.1:4646/.well-known/jwks.json}"
VAULT_ADDR_DEFAULT="${VAULT_ADDR_DEFAULT:-http://127.0.0.1:8200}"
VAULT_PUBLIC_ADDR="${VAULT_PUBLIC_ADDR:-https://vault.jorisjonkers.dev}"
AUTH_ISSUER="${AUTH_ISSUER:-https://auth.jorisjonkers.dev}"
DB_ENGINE_HOST="${DB_ENGINE_HOST:-127.0.0.1}"
DB_ENGINE_PORT="${DB_ENGINE_PORT:-5432}"

# ── Usage ──────────────────────────────────────────────────────────────────

usage() {
  cat <<'EOF'
Usage: setup.sh <command> [--dry-run]

Commands:
  install          Install Docker, Consul, Nomad, Vault packages
  configure        Write configs, create volumes, set UFW rules, enable services
  init-vault       Initialize and unseal Vault, enable KV engine
  init-nomad       Bootstrap Nomad ACL
  seed-secrets     Seed Vault KV from .nomad-bootstrap.env
  prepare-vault    Configure JWT auth, policies, roles, transit, database engine, OIDC
  rotate-secrets   Rotate PostgreSQL and RabbitMQ passwords
  full             Run the complete bootstrap sequence including deploy
EOF
}

# ── Utility functions ──────────────────────────────────────────────────────

run() {
  echo "+ $*"
  [[ "${MODE}" == "apply" ]] && "$@"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || { echo "Missing command: $1" >&2; exit 1; }
}

require_root() {
  [[ "$(id -u)" -eq 0 ]] || { echo "Run as root." >&2; exit 1; }
}

shell_single_quote() {
  printf "'%s'" "${1//\'/\'\"\'\"\'}"
}

load_bootstrap_env() {
  if [[ -f "${BOOTSTRAP_ENV_FILE}" ]]; then
    set -a; source "${BOOTSTRAP_ENV_FILE}"; set +a
  fi
}

load_vault_context() {
  load_bootstrap_env
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

ensure_vault_unsealed() {
  if ! vault status -format=json 2>/dev/null | jq -e '.sealed == false' >/dev/null 2>&1; then
    if [[ -z "${VAULT_UNSEAL_KEY:-}" && -f "${VAULT_KEYS_FILE}" ]]; then
      source "${VAULT_KEYS_FILE}"
    fi
    if [[ -n "${VAULT_UNSEAL_KEY:-}" ]]; then
      echo "+ vault operator unseal"
      vault operator unseal "${VAULT_UNSEAL_KEY}" >/dev/null
    else
      echo "Vault is sealed and no unseal key is available." >&2
      exit 1
    fi
  fi
}

write_text_file() {
  local path="$1" permissions="$2"
  if [[ "${MODE}" == "dry-run" ]]; then
    echo "+ write ${path}"; cat >/dev/null; return
  fi
  mkdir -p "$(dirname "${path}")"
  cat >"${path}"
  chmod "${permissions}" "${path}"
}

ensure_bootstrap_env_line() {
  local key="$1" value="$2" quoted
  quoted="$(shell_single_quote "${value}")"
  if [[ "${MODE}" == "dry-run" ]]; then
    echo "+ upsert ${key} in ${BOOTSTRAP_ENV_FILE}"; return
  fi
  mkdir -p "$(dirname "${BOOTSTRAP_ENV_FILE}")"
  touch "${BOOTSTRAP_ENV_FILE}"
  chmod 600 "${BOOTSTRAP_ENV_FILE}"
  if grep -q "^${key}=" "${BOOTSTRAP_ENV_FILE}"; then
    sed -i "s|^${key}=.*|${key}=${quoted}|" "${BOOTSTRAP_ENV_FILE}"
  else
    printf '%s=%s\n' "${key}" "${quoted}" >>"${BOOTSTRAP_ENV_FILE}"
  fi
}

require_bootstrap_var() {
  local name="$1"
  [[ -n "${!name:-}" ]] || { echo "Missing bootstrap variable: ${name}" >&2; exit 1; }
}

random_secret() { openssl rand -hex 32; }

wait_for_http() {
  local url="$1" description="$2" timeout="${3:-60}" elapsed=0
  while (( elapsed < timeout )); do
    curl -sS -o /dev/null "${url}" >/dev/null 2>&1 && return 0
    sleep 2; elapsed=$((elapsed + 2))
  done
  echo "Timed out waiting for ${description} (${url})." >&2; exit 1
}

wait_for_systemd() {
  local service="$1" timeout="${2:-60}" elapsed=0
  while (( elapsed < timeout )); do
    systemctl is-active --quiet "${service}" && return 0
    sleep 2; elapsed=$((elapsed + 2))
  done
  systemctl status "${service}" --no-pager || true
  echo "Timed out waiting for ${service}." >&2; exit 1
}

wait_for_nomad_api() {
  load_nomad_context
  wait_for_http "${NOMAD_ADDR}/v1/status/leader" "Nomad API" 90
}

wait_for_postgres() {
  load_bootstrap_env
  local postgres_user="${POSTGRES_USER:-postgres}" timeout="${1:-120}" elapsed=0
  if ! command -v pg_isready >/dev/null 2>&1; then
    echo "pg_isready not installed; skipping PostgreSQL readiness probe."; return
  fi
  while (( elapsed < timeout )); do
    pg_isready -h "${DB_ENGINE_HOST}" -p "${DB_ENGINE_PORT}" -U "${postgres_user}" >/dev/null 2>&1 && return 0
    sleep 3; elapsed=$((elapsed + 3))
  done
  echo "Timed out waiting for PostgreSQL on ${DB_ENGINE_HOST}:${DB_ENGINE_PORT}." >&2; exit 1
}

wait_for_job_running() {
  local job="$1" timeout="${2:-180}" elapsed=0
  load_nomad_context
  while (( elapsed < timeout )); do
    if nomad job allocs -json "${job}" 2>/dev/null \
        | jq -e 'length > 0 and all(.[]; .ClientStatus == "running")' >/dev/null 2>&1; then
      return 0
    fi
    sleep 3; elapsed=$((elapsed + 3))
  done
  nomad job status "${job}" || true
  echo "Timed out waiting for Nomad job ${job}." >&2; exit 1
}

detect_primary_ip() {
  local ip
  ip="$(ip -4 route get 1.1.1.1 2>/dev/null | grep -oP 'src \K\S+' || true)"
  [[ -z "${ip}" ]] && ip="$(hostname -I | awk '{print $1}')"
  printf '%s' "${ip}"
}

upsert_kv() {
  local path="$1"; shift
  vault kv patch "${path}" "$@" >/dev/null 2>&1 || vault kv put "${path}" "$@" >/dev/null
}

write_policy() {
  local name="$1" file="$2"
  echo "+ vault policy write ${name} ${file}"
  vault policy write "${name}" "${file}"
}

write_role() {
  local role="$1" file="$2"
  echo "+ vault write auth/jwt-nomad/role/${role} - < ${file}"
  vault write "auth/jwt-nomad/role/${role}" - < "${file}"
}

# ── install command ────────────────────────────────────────────────────────

install_command() {
  require_root
  load_bootstrap_env

  run apt-get update
  run apt-get install -y gpg curl unzip ca-certificates lsb-release jq dnsutils postgresql-client

  # Docker
  if ! command -v docker >/dev/null 2>&1; then
    run curl -fsSL https://get.docker.com -o /tmp/get-docker.sh
    run sh /tmp/get-docker.sh
  fi
  run systemctl enable docker
  run systemctl start docker
  if id deploy >/dev/null 2>&1; then
    run usermod -aG docker deploy
  fi

  # HashiCorp repository
  if [[ ! -f /usr/share/keyrings/hashicorp-archive-keyring.gpg ]]; then
    run curl -fsSL https://apt.releases.hashicorp.com/gpg -o /tmp/hashicorp.gpg
    run gpg --dearmor -o /usr/share/keyrings/hashicorp-archive-keyring.gpg /tmp/hashicorp.gpg
  fi
  if [[ ! -f /etc/apt/sources.list.d/hashicorp.list ]]; then
    if [[ "${MODE}" == "apply" ]]; then
      echo "deb [signed-by=/usr/share/keyrings/hashicorp-archive-keyring.gpg] https://apt.releases.hashicorp.com $(lsb_release -cs) main" \
        | tee /etc/apt/sources.list.d/hashicorp.list >/dev/null
    else
      echo "+ write /etc/apt/sources.list.d/hashicorp.list"
    fi
  fi
  run apt-get update
  run apt-get install -y consul nomad vault

  # CNI plugins (required for Nomad bridge networking)
  if [[ ! -f /opt/cni/bin/bridge ]]; then
    CNI_VERSION="v1.4.0"
    run mkdir -p /opt/cni/bin
    run curl -sL "https://github.com/containernetworking/plugins/releases/download/${CNI_VERSION}/cni-plugins-linux-amd64-${CNI_VERSION}.tgz" \
      -o /tmp/cni-plugins.tgz
    run tar -xz -C /opt/cni/bin -f /tmp/cni-plugins.tgz
    run rm -f /tmp/cni-plugins.tgz
  fi

  # Data directories
  run mkdir -p /etc/consul.d /etc/nomad.d /etc/vault.d
  run mkdir -p /opt/consul /opt/nomad /opt/vault/data
  run mkdir -p /srv/nomad/postgres /srv/nomad/prometheus /srv/nomad/traefik /srv/nomad/valkey
  run mkdir -p /srv/nomad/rabbitmq /srv/nomad/n8n /srv/nomad/grafana /srv/nomad/loki
  run mkdir -p /srv/nomad/tempo /srv/nomad/uptime-kuma /srv/nomad/stalwart

  run chown -R consul:consul /opt/consul
  run chown -R nomad:nomad /opt/nomad
  run chown -R vault:vault /opt/vault

  # Volume ownership matching container UIDs
  run chown -R 70:70       /srv/nomad/postgres      # postgres
  run chown -R 65534:65534 /srv/nomad/prometheus     # nobody
  run chown -R 999:999     /srv/nomad/valkey         # valkey
  run chown -R 999:999     /srv/nomad/rabbitmq       # rabbitmq
  run chown -R 472:472     /srv/nomad/grafana        # grafana
  run chown -R 10001:10001 /srv/nomad/loki           # loki
  run chown -R 10001:10001 /srv/nomad/tempo          # tempo
  run chown -R 1000:1000   /srv/nomad/n8n            # node
  run chown -R 1000:1000   /srv/nomad/uptime-kuma    # node

  # GHCR login
  if [[ -n "${GHCR_USER:-}" && -n "${GHCR_TOKEN:-}" ]]; then
    echo "+ docker login ghcr.io"
    if [[ "${MODE}" == "apply" ]]; then
      printf '%s' "${GHCR_TOKEN}" | docker login ghcr.io -u "${GHCR_USER}" --password-stdin >/dev/null
      if id deploy >/dev/null 2>&1; then
        su - deploy -c "printf '%s' $(shell_single_quote "${GHCR_TOKEN}") | docker login ghcr.io -u $(shell_single_quote "${GHCR_USER}") --password-stdin >/dev/null"
      fi
    fi
  fi

  echo "Package installation complete."
}

# ── configure command ──────────────────────────────────────────────────────

configure_command() {
  require_root

  local primary_ip
  primary_ip="$(detect_primary_ip)"
  [[ -n "${primary_ip}" ]] || { echo "Could not detect primary IP." >&2; exit 1; }

  # Install config files
  install -m 0644 "${ROOT_DIR}/infra/nomad/configs/consul.hcl" /etc/consul.d/consul.hcl
  install -m 0644 "${ROOT_DIR}/infra/nomad/configs/nomad.hcl"  /etc/nomad.d/nomad.hcl
  install -m 0644 "${ROOT_DIR}/infra/nomad/configs/vault.hcl"  /etc/vault.d/vault.hcl

  # Consul needs the host IP for cluster advertisement
  sed -i "s/__BIND_ADDR__/${primary_ip}/g" /etc/consul.d/consul.hcl
  echo "Consul bind_addr set to ${primary_ip}"

  ensure_bootstrap_env_line HOST_IP "${primary_ip}"

  # Firewall: default deny, whitelist only public-facing ports
  if command -v ufw >/dev/null 2>&1; then
    run ufw default deny incoming
    run ufw default allow outgoing
    run ufw allow 2222/tcp  comment 'ssh'
    run ufw allow 80/tcp    comment 'http'
    run ufw allow 443/tcp   comment 'https'
    run ufw allow 25/tcp    comment 'smtp'
    run ufw allow 110/tcp   comment 'pop3'
    run ufw allow 143/tcp   comment 'imap'
    run ufw allow 465/tcp   comment 'smtps'
    run ufw allow 587/tcp   comment 'submission'
    run ufw allow 993/tcp   comment 'imaps'
    run ufw allow 995/tcp   comment 'pop3s'
    run ufw allow 4190/tcp  comment 'sieve'
    run ufw --force enable
  fi

  # Systemd ordering: Nomad starts after Docker, Consul, and Vault
  cat <<'EOF' | write_text_file /etc/systemd/system/nomad.service.d/override.conf 0644
[Unit]
After=network-online.target docker.service consul.service vault.service
Wants=network-online.target docker.service consul.service vault.service
EOF

  # Auto-unseal service: unseals Vault after boot/restart
  cat <<'UNIT' | write_text_file /etc/systemd/system/vault-unseal.service 0644
[Unit]
Description=Unseal Vault after start
After=vault.service
Requires=vault.service

[Service]
Type=oneshot
RemainAfterExit=yes
EnvironmentFile=/opt/personal-stack/.vault-keys
Environment=VAULT_ADDR=http://127.0.0.1:8200
ExecStartPre=/bin/bash -c 'for i in $(seq 1 30); do curl -sf http://127.0.0.1:8200/v1/sys/health > /dev/null 2>&1 && exit 0; sleep 2; done; exit 1'
ExecStart=/usr/bin/vault operator unseal ${VAULT_UNSEAL_KEY}

[Install]
WantedBy=multi-user.target
UNIT

  run systemctl daemon-reload
  run systemctl enable consul vault nomad

  # Start services
  systemctl restart consul || true
  wait_for_http "http://127.0.0.1:8500/v1/status/leader" "Consul API" 60

  systemctl restart vault || true
  wait_for_http "http://127.0.0.1:8200/v1/sys/health" "Vault API" 90

  systemctl restart nomad || true
  wait_for_nomad_api

  echo "Host configuration complete."
}

# ── init-vault command ─────────────────────────────────────────────────────

init_vault_command() {
  require_command vault
  require_command jq
  load_bootstrap_env
  load_vault_context

  wait_for_http "${VAULT_ADDR}/v1/sys/health" "Vault API" 90

  if [[ ! -f "${VAULT_KEYS_FILE}" ]]; then
    if vault status -format=json 2>/dev/null | jq -e '.initialized == true' >/dev/null 2>&1; then
      echo "Vault is already initialized but ${VAULT_KEYS_FILE} is missing." >&2; exit 1
    fi

    echo "+ vault operator init -key-shares=1 -key-threshold=1"
    local init_response unseal_key root_token
    init_response="$(vault operator init -key-shares=1 -key-threshold=1 -format=json)"
    unseal_key="$(printf '%s' "${init_response}" | jq -r '.unseal_keys_b64[0]')"
    root_token="$(printf '%s' "${init_response}" | jq -r '.root_token')"

    cat <<EOF | write_text_file "${VAULT_KEYS_FILE}" 0640
VAULT_UNSEAL_KEY=$(shell_single_quote "${unseal_key}")
VAULT_ROOT_TOKEN=$(shell_single_quote "${root_token}")
EOF
    if id deploy >/dev/null 2>&1; then
      chown root:deploy "${VAULT_KEYS_FILE}" 2>/dev/null || true
    fi
  fi

  source "${VAULT_KEYS_FILE}"
  export VAULT_TOKEN="${VAULT_ROOT_TOKEN}"

  if vault status -format=json 2>/dev/null | jq -e '.sealed == true' >/dev/null 2>&1; then
    echo "+ vault operator unseal"
    [[ "${MODE}" == "apply" ]] && vault operator unseal "${VAULT_UNSEAL_KEY}" >/dev/null
  fi

  if [[ "${MODE}" == "apply" ]]; then
    vault secrets enable -path=secret -version=2 kv 2>/dev/null || true
  fi

  # Enable the auto-unseal service now that keys exist
  run systemctl enable vault-unseal 2>/dev/null || true

  echo "Vault initialized and unsealed."
}

# ── init-nomad command ─────────────────────────────────────────────────────

init_nomad_command() {
  require_command nomad
  require_command jq
  load_nomad_context

  wait_for_nomad_api

  if [[ -f "${NOMAD_KEYS_FILE}" ]]; then
    source "${NOMAD_KEYS_FILE}"
    export NOMAD_TOKEN="${NOMAD_BOOTSTRAP_TOKEN:-}"
    return
  fi

  echo "+ nomad acl bootstrap -json"
  local bootstrap_response bootstrap_token
  if ! bootstrap_response="$(nomad acl bootstrap -json 2>/dev/null)"; then
    echo "Nomad ACL already bootstrapped but ${NOMAD_KEYS_FILE} is missing." >&2; exit 1
  fi
  bootstrap_token="$(printf '%s' "${bootstrap_response}" | jq -r '.SecretID')"

  cat <<EOF | write_text_file "${NOMAD_KEYS_FILE}" 0640
NOMAD_BOOTSTRAP_TOKEN=$(shell_single_quote "${bootstrap_token}")
EOF
  if id deploy >/dev/null 2>&1; then
    chown root:deploy "${NOMAD_KEYS_FILE}" 2>/dev/null || true
  fi

  export NOMAD_TOKEN="${bootstrap_token}"
}

# ── seed-secrets command ───────────────────────────────────────────────────

persist_bootstrap_secrets() {
  ensure_bootstrap_env_line POSTGRES_USER "${POSTGRES_USER}"
  ensure_bootstrap_env_line POSTGRES_PASSWORD "${POSTGRES_PASSWORD}"
  ensure_bootstrap_env_line AUTH_DB_USER "${AUTH_DB_USER}"
  ensure_bootstrap_env_line AUTH_DB_PASSWORD "${AUTH_DB_PASSWORD}"
  ensure_bootstrap_env_line ASSISTANT_DB_USER "${ASSISTANT_DB_USER}"
  ensure_bootstrap_env_line ASSISTANT_DB_PASSWORD "${ASSISTANT_DB_PASSWORD}"
  ensure_bootstrap_env_line N8N_DB_USER "${N8N_DB_USER}"
  ensure_bootstrap_env_line N8N_DB_PASSWORD "${N8N_DB_PASSWORD}"
  ensure_bootstrap_env_line RABBITMQ_USER "${RABBITMQ_USER}"
  ensure_bootstrap_env_line RABBITMQ_PASSWORD "${RABBITMQ_PASSWORD}"
  ensure_bootstrap_env_line CF_DNS_API_TOKEN "${CF_DNS_API_TOKEN}"
  ensure_bootstrap_env_line GRAFANA_ADMIN_USER "${GRAFANA_ADMIN_USER}"
  ensure_bootstrap_env_line GRAFANA_ADMIN_PASSWORD "${GRAFANA_ADMIN_PASSWORD}"
  ensure_bootstrap_env_line STALWART_ADMIN_USER "${STALWART_ADMIN_USER}"
  ensure_bootstrap_env_line STALWART_ADMIN_PASSWORD "${STALWART_ADMIN_PASSWORD}"
  ensure_bootstrap_env_line N8N_OAUTH_CLIENT_SECRET "${N8N_OAUTH_CLIENT_SECRET}"
  ensure_bootstrap_env_line GRAFANA_OAUTH_CLIENT_SECRET "${GRAFANA_OAUTH_CLIENT_SECRET}"
  ensure_bootstrap_env_line VAULT_OIDC_CLIENT_SECRET "${VAULT_OIDC_CLIENT_SECRET}"
  ensure_bootstrap_env_line STALWART_OAUTH_CLIENT_SECRET "${STALWART_OAUTH_CLIENT_SECRET}"
}

write_all_secrets_to_vault() {
  upsert_kv secret/auth-api \
    "spring.datasource.username=${AUTH_DB_USER}" \
    "spring.datasource.password=${AUTH_DB_PASSWORD}" \
    "spring.rabbitmq.username=${RABBITMQ_USER}" \
    "spring.rabbitmq.password=${RABBITMQ_PASSWORD}" \
    "auth.clients.grafana.secret=${GRAFANA_OAUTH_CLIENT_SECRET}" \
    "auth.clients.n8n.secret=${N8N_OAUTH_CLIENT_SECRET}" \
    "auth.clients.vault.secret=${VAULT_OIDC_CLIENT_SECRET}" \
    "auth.clients.stalwart.secret=${STALWART_OAUTH_CLIENT_SECRET}"

  upsert_kv secret/assistant-api \
    "spring.datasource.username=${ASSISTANT_DB_USER}" \
    "spring.datasource.password=${ASSISTANT_DB_PASSWORD}" \
    "spring.rabbitmq.username=${RABBITMQ_USER}" \
    "spring.rabbitmq.password=${RABBITMQ_PASSWORD}"

  vault kv put secret/platform/postgres \
    "postgres.user=${POSTGRES_USER}" \
    "postgres.password=${POSTGRES_PASSWORD}" \
    "auth.user=${AUTH_DB_USER}" \
    "auth.password=${AUTH_DB_PASSWORD}" \
    "assistant.user=${ASSISTANT_DB_USER}" \
    "assistant.password=${ASSISTANT_DB_PASSWORD}" \
    "n8n.user=${N8N_DB_USER}" \
    "n8n.password=${N8N_DB_PASSWORD}" >/dev/null

  vault kv put secret/platform/rabbitmq \
    "rabbitmq.user=${RABBITMQ_USER}" \
    "rabbitmq.password=${RABBITMQ_PASSWORD}" >/dev/null

  vault kv put secret/platform/edge \
    "cloudflare.dns_api_token=${CF_DNS_API_TOKEN}" >/dev/null

  vault kv put secret/platform/automation \
    "n8n.db_user=${N8N_DB_USER}" \
    "n8n.db_password=${N8N_DB_PASSWORD}" \
    "n8n.oauth_client_secret=${N8N_OAUTH_CLIENT_SECRET}" >/dev/null

  vault kv put secret/platform/observability \
    "grafana.admin_user=${GRAFANA_ADMIN_USER}" \
    "grafana.admin_password=${GRAFANA_ADMIN_PASSWORD}" \
    "grafana.oauth_client_secret=${GRAFANA_OAUTH_CLIENT_SECRET}" >/dev/null

  vault kv put secret/platform/mail \
    "stalwart.admin_user=${STALWART_ADMIN_USER}" \
    "stalwart.admin_password=${STALWART_ADMIN_PASSWORD}" \
    "stalwart.oauth_client_secret=${STALWART_OAUTH_CLIENT_SECRET}" >/dev/null
}

seed_secrets_command() {
  load_bootstrap_env
  load_vault_context
  : "${VAULT_ADDR:?Set VAULT_ADDR}"
  : "${VAULT_TOKEN:?Set VAULT_TOKEN}"

  : "${POSTGRES_USER:=postgres}"
  : "${AUTH_DB_USER:=auth_user}"
  : "${ASSISTANT_DB_USER:=assistant_user}"
  : "${N8N_DB_USER:=n8n_user}"
  : "${RABBITMQ_USER:=appuser}"
  : "${GRAFANA_ADMIN_USER:=admin}"
  : "${STALWART_ADMIN_USER:=admin}"

  require_bootstrap_var POSTGRES_PASSWORD
  require_bootstrap_var AUTH_DB_PASSWORD
  require_bootstrap_var ASSISTANT_DB_PASSWORD
  require_bootstrap_var N8N_DB_PASSWORD
  require_bootstrap_var RABBITMQ_PASSWORD
  require_bootstrap_var CF_DNS_API_TOKEN
  require_bootstrap_var GRAFANA_ADMIN_PASSWORD
  require_bootstrap_var STALWART_ADMIN_PASSWORD

  [[ -n "${N8N_OAUTH_CLIENT_SECRET:-}" ]]      || { N8N_OAUTH_CLIENT_SECRET="$(random_secret)";      ensure_bootstrap_env_line N8N_OAUTH_CLIENT_SECRET "${N8N_OAUTH_CLIENT_SECRET}"; }
  [[ -n "${GRAFANA_OAUTH_CLIENT_SECRET:-}" ]]   || { GRAFANA_OAUTH_CLIENT_SECRET="$(random_secret)";   ensure_bootstrap_env_line GRAFANA_OAUTH_CLIENT_SECRET "${GRAFANA_OAUTH_CLIENT_SECRET}"; }
  [[ -n "${VAULT_OIDC_CLIENT_SECRET:-}" ]]      || { VAULT_OIDC_CLIENT_SECRET="$(random_secret)";      ensure_bootstrap_env_line VAULT_OIDC_CLIENT_SECRET "${VAULT_OIDC_CLIENT_SECRET}"; }
  [[ -n "${STALWART_OAUTH_CLIENT_SECRET:-}" ]]  || { STALWART_OAUTH_CLIENT_SECRET="$(random_secret)";  ensure_bootstrap_env_line STALWART_OAUTH_CLIENT_SECRET "${STALWART_OAUTH_CLIENT_SECRET}"; }

  if [[ "${MODE}" == "dry-run" ]]; then
    echo "+ seed Vault KV from ${BOOTSTRAP_ENV_FILE}"; return
  fi

  persist_bootstrap_secrets
  write_all_secrets_to_vault
  echo "Bootstrap secrets seeded into Vault KV."
}

# ── prepare-vault command ──────────────────────────────────────────────────

configure_database_engine() {
  local postgres_user postgres_password auth_db_user assistant_db_user

  if vault read database/config/postgres >/dev/null 2>&1 &&
    vault read database/roles/auth-api >/dev/null 2>&1 &&
    vault read database/roles/assistant-api >/dev/null 2>&1; then
    echo "Vault database engine already configured."; return
  fi

  postgres_user="$(vault kv get -field=postgres.user secret/platform/postgres 2>/dev/null || true)"
  postgres_password="$(vault kv get -field=postgres.password secret/platform/postgres 2>/dev/null || true)"
  auth_db_user="$(vault kv get -field=auth.user secret/platform/postgres 2>/dev/null || true)"
  assistant_db_user="$(vault kv get -field=assistant.user secret/platform/postgres 2>/dev/null || true)"

  if [[ -z "${postgres_user}" || -z "${postgres_password}" ]]; then
    echo "Skipping database engine: secret/platform/postgres not populated yet."; return
  fi

  : "${auth_db_user:=auth_user}"
  : "${assistant_db_user:=assistant_user}"

  if command -v pg_isready >/dev/null 2>&1; then
    if ! pg_isready -h "${DB_ENGINE_HOST}" -p "${DB_ENGINE_PORT}" -U "${postgres_user}" >/dev/null 2>&1; then
      echo "Skipping database engine: PostgreSQL not reachable yet."; return
    fi
  fi

  echo "+ vault write database/config/postgres"
  if ! vault write database/config/postgres \
    plugin_name=postgresql-database-plugin \
    "connection_url=postgresql://{{username}}:{{password}}@${DB_ENGINE_HOST}:${DB_ENGINE_PORT}/postgres?sslmode=disable" \
    allowed_roles="auth-api,assistant-api" \
    "username=${postgres_user}" \
    "password=${postgres_password}" >/dev/null; then
    echo "Skipping database engine: could not configure connection."; return
  fi

  vault write -force database/rotate-root/postgres >/dev/null 2>&1 || true

  echo "+ vault write database/roles/auth-api"
  vault write database/roles/auth-api \
    db_name=postgres \
    creation_statements="
      CREATE ROLE \"{{name}}\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}';
      GRANT CONNECT ON DATABASE auth_db TO \"{{name}}\";
      GRANT USAGE ON SCHEMA public TO \"{{name}}\";
      GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO \"{{name}}\";
      GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO \"{{name}}\";
      ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO \"{{name}}\";
      ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON SEQUENCES TO \"{{name}}\";" \
    revocation_statements="
      REASSIGN OWNED BY \"{{name}}\" TO ${auth_db_user};
      DROP OWNED BY \"{{name}}\";
      DROP ROLE IF EXISTS \"{{name}}\";" \
    default_ttl=1h max_ttl=24h >/dev/null

  echo "+ vault write database/roles/assistant-api"
  vault write database/roles/assistant-api \
    db_name=postgres \
    creation_statements="
      CREATE ROLE \"{{name}}\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}';
      GRANT CONNECT ON DATABASE assistant_db TO \"{{name}}\";
      GRANT USAGE ON SCHEMA public TO \"{{name}}\";
      GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO \"{{name}}\";
      GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO \"{{name}}\";
      ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO \"{{name}}\";
      ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON SEQUENCES TO \"{{name}}\";" \
    revocation_statements="
      REASSIGN OWNED BY \"{{name}}\" TO ${assistant_db_user};
      DROP OWNED BY \"{{name}}\";
      DROP ROLE IF EXISTS \"{{name}}\";" \
    default_ttl=1h max_ttl=24h >/dev/null
}

configure_vault_oidc_auth() {
  local vault_oauth_secret
  vault_oauth_secret="$(vault kv get -field="auth.clients.vault.secret" secret/auth-api 2>/dev/null || true)"
  [[ -n "${vault_oauth_secret}" ]] || {
    echo "Skipping OIDC: auth.clients.vault.secret not available yet."; return
  }

  vault auth enable oidc >/dev/null 2>&1 || true

  echo "+ vault write auth/oidc/config"
  vault write auth/oidc/config \
    oidc_discovery_url="${AUTH_ISSUER}" \
    oidc_client_id="vault" \
    oidc_client_secret="${vault_oauth_secret}" \
    default_role="default" >/dev/null

  echo "+ vault write auth/oidc/role/default"
  vault write auth/oidc/role/default - <<EOF >/dev/null
{
  "bound_audiences": "vault",
  "allowed_redirect_uris": [
    "${VAULT_PUBLIC_ADDR}/ui/vault/auth/oidc/oidc/callback",
    "http://localhost:8250/oidc/callback"
  ],
  "user_claim": "sub",
  "groups_claim": "roles",
  "oidc_scopes": ["openid", "profile", "email"],
  "bound_claims": {
    "roles": ["ROLE_ADMIN", "SERVICE_VAULT"]
  },
  "token_policies": ["admin"]
}
EOF
}

prepare_vault_command() {
  load_bootstrap_env
  load_vault_context
  ensure_vault_unsealed
  : "${VAULT_ADDR:?Set VAULT_ADDR}"
  : "${VAULT_TOKEN:?Set VAULT_TOKEN}"

  if [[ "${MODE}" == "dry-run" ]]; then
    echo "+ enable engines, write policies/roles, configure transit, database, OIDC"; return
  fi

  export VAULT_ADDR VAULT_TOKEN

  # Enable engines (idempotent)
  vault secrets enable -path=secret -version=2 kv 2>/dev/null || true
  vault auth enable -path=jwt-nomad jwt 2>/dev/null || true
  vault secrets enable database 2>/dev/null || true
  vault secrets enable transit 2>/dev/null || true

  # JWT auth for Nomad workload identity
  echo "+ vault write auth/jwt-nomad/config"
  vault write auth/jwt-nomad/config - <<EOF >/dev/null
{
  "jwks_url": "${NOMAD_JWKS_URL}",
  "jwt_supported_algs": ["RS256", "EdDSA"],
  "default_role": "nomad-workloads"
}
EOF

  # Transit key for JWT signing
  if ! vault read "transit/keys/auth-api-jwt" >/dev/null 2>&1; then
    echo "+ vault write transit/keys/auth-api-jwt"
    vault write transit/keys/auth-api-jwt type="rsa-2048" >/dev/null
  fi

  # Policies
  while IFS= read -r policy_file; do
    write_policy "$(basename "${policy_file}" .hcl)" "${policy_file}"
  done < <(find "${VAULT_DIR}/policies" -type f -name "*.hcl" | sort)

  # JWT roles
  while IFS= read -r role_file; do
    write_role "$(basename "${role_file}" .json)" "${role_file}"
  done < <(find "${VAULT_DIR}/roles" -type f -name "*.json" | sort)

  # Runtime configuration (requires running services)
  configure_database_engine
  configure_vault_oidc_auth

  echo "Vault configuration complete."
}

# ── rotate-secrets command ─────────────────────────────────────────────────

rotate_secrets_command() {
  require_command vault
  require_command openssl
  load_vault_context
  ensure_vault_unsealed
  : "${VAULT_ADDR:?Set VAULT_ADDR}"
  : "${VAULT_TOKEN:?Set VAULT_TOKEN}"
  export VAULT_ADDR VAULT_TOKEN

  local pg_host="${DB_ENGINE_HOST}" pg_port="${DB_ENGINE_PORT}" errors=0
  local postgres_user rabbitmq_user
  postgres_user="$(vault kv get -field=postgres.user secret/platform/postgres 2>/dev/null || echo postgres)"
  rabbitmq_user="$(vault kv get -field=rabbitmq.user secret/platform/rabbitmq 2>/dev/null || echo appuser)"

  echo "=== Rotating PostgreSQL service passwords ==="
  local n8n_new_pass
  n8n_new_pass="$(random_secret)"
  if [[ "${MODE}" == "apply" ]]; then
    if PGPASSWORD="$(vault kv get -field=postgres.password secret/platform/postgres 2>/dev/null)" \
       psql -h "${pg_host}" -p "${pg_port}" -U "${postgres_user}" -d postgres \
       -c "ALTER USER n8n_user WITH PASSWORD '${n8n_new_pass}';" >/dev/null 2>&1; then
      vault kv patch secret/platform/postgres "n8n.password=${n8n_new_pass}" >/dev/null
      vault kv patch secret/platform/automation "n8n.db_password=${n8n_new_pass}" >/dev/null
      echo "  n8n DB password rotated."
    else
      echo "  WARNING: Failed to rotate n8n DB password." >&2
      errors=$((errors + 1))
    fi
  fi

  echo "=== Rotating RabbitMQ password ==="
  local rmq_new_pass
  rmq_new_pass="$(random_secret)"
  if [[ "${MODE}" == "apply" ]]; then
    local rmq_old_pass
    rmq_old_pass="$(vault kv get -field=rabbitmq.password secret/platform/rabbitmq 2>/dev/null)"
    if curl -sf -u "${rabbitmq_user}:${rmq_old_pass}" \
       -X PUT "http://127.0.0.1:15672/api/users/${rabbitmq_user}" \
       -H 'content-type: application/json' \
       -d "{\"password\":\"${rmq_new_pass}\",\"tags\":\"administrator\"}" >/dev/null; then
      vault kv put secret/platform/rabbitmq \
        "rabbitmq.user=${rabbitmq_user}" "rabbitmq.password=${rmq_new_pass}" >/dev/null
      upsert_kv secret/auth-api \
        "spring.rabbitmq.username=${rabbitmq_user}" "spring.rabbitmq.password=${rmq_new_pass}"
      upsert_kv secret/assistant-api \
        "spring.rabbitmq.username=${rabbitmq_user}" "spring.rabbitmq.password=${rmq_new_pass}"
      echo "  RabbitMQ password rotated."
    else
      echo "  WARNING: Failed to rotate RabbitMQ password." >&2
      errors=$((errors + 1))
    fi
  fi

  if [[ "${errors}" -gt 0 ]]; then
    echo "Rotation completed with ${errors} error(s)." >&2; exit 1
  fi
  echo "Secret rotation complete."
}

# ── full command ───────────────────────────────────────────────────────────

full_command() {
  require_root

  install_command
  configure_command

  require_command jq
  require_command curl
  require_command nomad
  require_command vault

  init_vault_command
  init_nomad_command
  seed_secrets_command
  prepare_vault_command

  # Deploy data tier first, wait for it
  local deploy_script="${ROOT_DIR}/infra/scripts/deploy.sh"
  bash "${deploy_script}" --phase data --wait

  wait_for_postgres 240

  # Re-run prepare-vault now that Postgres is available for the database engine
  prepare_vault_command

  # Deploy everything
  bash "${deploy_script}" --phase all --wait

  echo "Full server bootstrap complete."
  echo "Credentials written to:"
  echo "  ${BOOTSTRAP_ENV_FILE}"
  echo "  ${VAULT_KEYS_FILE}"
  echo "  ${NOMAD_KEYS_FILE}"
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
    install)        install_command ;;
    configure)      configure_command ;;
    init-vault)     init_vault_command ;;
    init-nomad)     init_nomad_command ;;
    seed-secrets)   seed_secrets_command ;;
    prepare-vault)  prepare_vault_command ;;
    rotate-secrets) rotate_secrets_command ;;
    full)           full_command ;;
    *)              echo "Unknown command: ${command}" >&2; usage; exit 1 ;;
  esac
}

main "$@"
