#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STACK_DIR="${STACK_DIR:-/opt/personal-stack}"
NOMAD_DIR="${ROOT_DIR}/infra/nomad"
VAULT_DIR="${NOMAD_DIR}/vault"
MODE="apply"
PHASE="all"
BACKUP_DIR="${BACKUP_DIR:-${ROOT_DIR}/tmp/nomad-migration}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
IMAGE_REPO="${IMAGE_REPO:-ghcr.io/extratoast/personal-stack}"
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
STACK_PREFIX="${STACK_PREFIX:-}"

usage() {
  cat <<'EOF'
Usage:
  infra/scripts/migrate-to-nomad.sh <command> [options]

Commands:
  bootstrap         Install Docker, Consul, Nomad, and Vault and configure the single-node host
  init-vault        Initialize and unseal the local Vault server
  seed-secrets      Seed Vault KV from bootstrap variables on a clean Nomad host
  bootstrap-server  Fully bootstrap a clean server into the Nomad deployment
  validate          Validate Nomad jobs, Consul configs, JSON roles, and this script
  prepare-vault     Configure Vault workload identity, policies/roles, Transit, OIDC, and DB engine
  sync-secrets      Copy live Swarm secrets into Vault KV for Nomad consumers
  deploy            Submit Nomad jobs
  cutover           Scale down Swarm ingress and app services after Nomad is ready
  rollback          Scale Swarm ingress and app services back up
  migrate           Backup Swarm state, sync secrets, prepare Vault, deploy data, re-prepare Vault, deploy all jobs

Options:
  --dry-run         Print commands without executing them
  --phase <name>    For deploy: data | all (default: all)
EOF
}

run() {
  echo "+ $*"
  if [[ "${MODE}" == "apply" ]]; then
    "$@"
  fi
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing command: $1" >&2
    exit 1
  }
}

require_root() {
  [[ "$(id -u)" -eq 0 ]] || {
    echo "Run as root." >&2
    exit 1
  }
}

parse_common_flags() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --dry-run)
        MODE="dry-run"
        ;;
      --phase)
        PHASE="${2:?Missing value for --phase}"
        shift
        ;;
      *)
        echo "Unknown argument: $1" >&2
        usage
        exit 1
        ;;
    esac
    shift
  done
}

shell_single_quote() {
  printf "'%s'" "${1//\'/\'\"\'\"\'}"
}

load_bootstrap_env() {
  if [[ -f "${BOOTSTRAP_ENV_FILE}" ]]; then
    set -a
    # shellcheck source=/dev/null
    source "${BOOTSTRAP_ENV_FILE}"
    set +a
  fi
}

load_vault_context() {
  load_bootstrap_env
  export VAULT_ADDR="${VAULT_ADDR:-${VAULT_ADDR_DEFAULT}}"

  if [[ -z "${VAULT_TOKEN:-}" && -f "${VAULT_KEYS_FILE}" ]]; then
    # shellcheck source=/dev/null
    source "${VAULT_KEYS_FILE}"
    export VAULT_TOKEN="${VAULT_ROOT_TOKEN:-}"
  fi
}

load_nomad_context() {
  export NOMAD_ADDR="${NOMAD_ADDR:-http://127.0.0.1:4646}"

  if [[ -z "${NOMAD_TOKEN:-}" && -f "${NOMAD_KEYS_FILE}" ]]; then
    # shellcheck source=/dev/null
    source "${NOMAD_KEYS_FILE}"
    export NOMAD_TOKEN="${NOMAD_BOOTSTRAP_TOKEN:-}"
  fi
}

write_text_file() {
  local path="$1"
  local permissions="$2"

  if [[ "${MODE}" == "dry-run" ]]; then
    echo "+ write ${path}"
    cat >/dev/null
    return
  fi

  mkdir -p "$(dirname "${path}")"
  cat >"${path}"
  chmod "${permissions}" "${path}"
}

copy_example() {
  local source_file="$1"
  local target_file="$2"
  local permissions="${3:-0644}"

  echo "+ install -m ${permissions} ${source_file} ${target_file}"
  if [[ "${MODE}" == "apply" ]]; then
    install -m "${permissions}" "${source_file}" "${target_file}"
  fi
}

ensure_bootstrap_env_line() {
  local key="$1"
  local value="$2"
  local quoted

  quoted="$(shell_single_quote "${value}")"

  if [[ "${MODE}" == "dry-run" ]]; then
    echo "+ upsert ${key} in ${BOOTSTRAP_ENV_FILE}"
    return
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
  [[ -n "${!name:-}" ]] || {
    echo "Missing bootstrap variable: ${name}" >&2
    exit 1
  }
}

wait_for_http() {
  local url="$1"
  local description="$2"
  local timeout="${3:-60}"
  local elapsed=0

  while (( elapsed < timeout )); do
    if curl -sS -o /dev/null "${url}" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done

  echo "Timed out waiting for ${description} (${url})." >&2
  exit 1
}

wait_for_systemd() {
  local service="$1"
  local timeout="${2:-60}"
  local elapsed=0

  while (( elapsed < timeout )); do
    if systemctl is-active --quiet "${service}"; then
      return 0
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done

  systemctl status "${service}" --no-pager || true
  echo "Timed out waiting for systemd service ${service}." >&2
  exit 1
}

wait_for_nomad_api() {
  load_nomad_context
  wait_for_http "${NOMAD_ADDR}/v1/status/leader" "Nomad API" 90
}

wait_for_postgres() {
  load_bootstrap_env

  local postgres_user="${POSTGRES_USER:-postgres}"
  local timeout="${1:-120}"
  local elapsed=0

  if ! command -v pg_isready >/dev/null 2>&1; then
    echo "pg_isready is not installed; skipping PostgreSQL readiness probe."
    return
  fi

  while (( elapsed < timeout )); do
    if pg_isready -h "${DB_ENGINE_HOST}" -p "${DB_ENGINE_PORT}" -U "${postgres_user}" >/dev/null 2>&1; then
      return 0
    fi
    sleep 3
    elapsed=$((elapsed + 3))
  done

  echo "Timed out waiting for PostgreSQL on ${DB_ENGINE_HOST}:${DB_ENGINE_PORT}." >&2
  exit 1
}

wait_for_job_running() {
  local job="$1"
  local timeout="${2:-180}"
  local elapsed=0

  load_nomad_context

  while (( elapsed < timeout )); do
    if nomad job allocs -json "${job}" 2>/dev/null | jq -e 'length > 0 and all(.[]; .ClientStatus == "running")' >/dev/null 2>&1; then
      return 0
    fi
    sleep 3
    elapsed=$((elapsed + 3))
  done

  nomad job status "${job}" || true
  echo "Timed out waiting for Nomad job ${job} to become running." >&2
  exit 1
}

install_docker() {
  if command -v docker >/dev/null 2>&1; then
    return
  fi

  run curl -fsSL https://get.docker.com -o /tmp/get-docker.sh
  run sh /tmp/get-docker.sh
}

docker_login_ghcr() {
  load_bootstrap_env

  if [[ -z "${GHCR_USER:-}" || -z "${GHCR_TOKEN:-}" ]]; then
    echo "Skipping GHCR login because GHCR_USER or GHCR_TOKEN is not set."
    return
  fi

  echo "+ docker login ghcr.io -u ${GHCR_USER} --password-stdin"
  if [[ "${MODE}" == "apply" ]]; then
    printf '%s' "${GHCR_TOKEN}" | docker login ghcr.io -u "${GHCR_USER}" --password-stdin >/dev/null

    if id deploy >/dev/null 2>&1; then
      su - deploy -c "printf '%s' $(shell_single_quote "${GHCR_TOKEN}") | docker login ghcr.io -u $(shell_single_quote "${GHCR_USER}") --password-stdin >/dev/null"
    fi
  fi
}

detect_stack_prefix_from_containers() {
  local names
  names="$(docker ps --format '{{.Names}}')"

  if printf '%s\n' "${names}" | grep -Eq '^personal-stack_'; then
    echo "personal-stack"
    return
  fi

  if printf '%s\n' "${names}" | grep -Eq '^private-stack_'; then
    echo "private-stack"
    return
  fi

  echo "Could not detect a running Swarm stack prefix." >&2
  exit 1
}

detect_stack_prefix_from_services() {
  local services
  services="$(docker service ls --format '{{.Name}}')"

  if printf '%s\n' "${services}" | grep -Eq '^personal-stack_'; then
    echo "personal-stack"
    return
  fi

  if printf '%s\n' "${services}" | grep -Eq '^private-stack_'; then
    echo "private-stack"
    return
  fi

  echo "Could not detect a deployed Swarm stack prefix." >&2
  exit 1
}

container_for_service() {
  local service="$1"
  docker ps --format '{{.Names}}' | grep -E "^${STACK_PREFIX}_${service}(\\.|$)" | head -n1
}

find_container() {
  local service="$1"
  local container
  container="$(container_for_service "${service}")"
  [[ -n "${container}" ]] || {
    echo "No running container found for service '${service}'." >&2
    exit 1
  }
  printf '%s' "${container}"
}

read_file_from_container() {
  local service="$1"
  local path="$2"
  local container
  container="$(container_for_service "${service}")"
  [[ -n "${container}" ]] || return 1
  docker exec "${container}" sh -lc "cat '${path}'" 2>/dev/null
}

read_secret_or_default() {
  local service="$1"
  local path="$2"
  local fallback="${3:-}"
  local value=""

  value="$(read_file_from_container "${service}" "${path}" || true)"
  if [[ -n "${value}" ]]; then
    printf '%s' "${value}"
  else
    printf '%s' "${fallback}"
  fi
}

random_secret() {
  openssl rand -hex 32
}

vault_field_or_default() {
  local path="$1"
  local field="$2"
  local fallback="${3:-}"
  local value=""

  value="$(vault kv get -field="${field}" "${path}" 2>/dev/null || true)"
  if [[ -n "${value}" ]]; then
    printf '%s' "${value}"
  else
    printf '%s' "${fallback}"
  fi
}

upsert_kv() {
  local path="$1"
  shift

  vault kv patch "${path}" "$@" >/dev/null 2>&1 || vault kv put "${path}" "$@" >/dev/null
}

write_policy() {
  local name="$1"
  local file="$2"
  echo "+ vault policy write ${name} ${file}"
  vault policy write "${name}" "${file}"
}

write_role() {
  local role="$1"
  local file="$2"
  echo "+ vault write auth/jwt-nomad/role/${role} - < ${file}"
  vault write "auth/jwt-nomad/role/${role}" - < "${file}"
}

ensure_transit_key() {
  if vault read "transit/keys/auth-api-jwt" >/dev/null 2>&1; then
    echo "Transit key auth-api-jwt already exists."
    return
  fi

  echo "+ vault write transit/keys/auth-api-jwt type=rsa-2048"
  vault write transit/keys/auth-api-jwt type="rsa-2048" >/dev/null
}

configure_database_engine() {
  local postgres_user postgres_password auth_db_user assistant_db_user

  if vault read database/config/postgres >/dev/null 2>&1 &&
    vault read database/roles/auth-api >/dev/null 2>&1 &&
    vault read database/roles/assistant-api >/dev/null 2>&1; then
    echo "Vault database secrets engine is already configured."
    return
  fi

  postgres_user="$(vault kv get -field=postgres.user secret/platform/postgres 2>/dev/null || true)"
  postgres_password="$(vault kv get -field=postgres.password secret/platform/postgres 2>/dev/null || true)"
  auth_db_user="$(vault kv get -field=auth.user secret/platform/postgres 2>/dev/null || true)"
  assistant_db_user="$(vault kv get -field=assistant.user secret/platform/postgres 2>/dev/null || true)"

  if [[ -z "${postgres_user}" || -z "${postgres_password}" ]]; then
    echo "Skipping database secrets engine setup: secret/platform/postgres is not populated yet."
    return
  fi

  : "${auth_db_user:=auth_user}"
  : "${assistant_db_user:=assistant_user}"

  if command -v pg_isready >/dev/null 2>&1; then
    if ! pg_isready -h "${DB_ENGINE_HOST}" -p "${DB_ENGINE_PORT}" -U "${postgres_user}" >/dev/null 2>&1; then
      echo "Skipping database secrets engine setup: PostgreSQL is not reachable at ${DB_ENGINE_HOST}:${DB_ENGINE_PORT} yet."
      return
    fi
  fi

  echo "+ vault write database/config/postgres ..."
  if ! vault write database/config/postgres \
    plugin_name=postgresql-database-plugin \
    "connection_url=postgresql://{{username}}:{{password}}@${DB_ENGINE_HOST}:${DB_ENGINE_PORT}/postgres?sslmode=disable" \
    allowed_roles="auth-api,assistant-api" \
    "username=${postgres_user}" \
    "password=${postgres_password}" >/dev/null; then
    echo "Skipping database secrets engine setup: Vault could not configure the PostgreSQL connection yet."
    return
  fi

  echo "+ vault write -force database/rotate-root/postgres"
  vault write -force database/rotate-root/postgres >/dev/null 2>&1 || true

  echo "+ vault write database/roles/auth-api ..."
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
    default_ttl=1h \
    max_ttl=24h >/dev/null

  echo "+ vault write database/roles/assistant-api ..."
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
    default_ttl=1h \
    max_ttl=24h >/dev/null
}

configure_vault_oidc_auth() {
  local vault_oauth_secret role_payload_file

  vault_oauth_secret="$(vault_field_or_default secret/auth-api "auth.clients.vault.secret")"
  [[ -n "${vault_oauth_secret}" ]] || {
    echo "Skipping Vault OIDC configuration: auth.clients.vault.secret is not available yet."
    return
  }

  echo "+ vault auth enable oidc"
  vault auth enable oidc >/dev/null 2>&1 || true

  echo "+ vault write auth/oidc/config ..."
  vault write auth/oidc/config \
    oidc_discovery_url="${AUTH_ISSUER}" \
    oidc_client_id="vault" \
    oidc_client_secret="${vault_oauth_secret}" \
    default_role="default" >/dev/null

  role_payload_file="$(mktemp)"
  cat >"${role_payload_file}" <<EOF
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

  echo "+ vault write auth/oidc/role/default @${role_payload_file}"
  vault write auth/oidc/role/default @"${role_payload_file}" >/dev/null
  rm -f "${role_payload_file}"
}

submit_job() {
  local file="$1"
  shift || true
  load_nomad_context
  run nomad job run "$@" "${file}"
}

deploy_data_jobs() {
  submit_job "${ROOT_DIR}/infra/nomad/jobs/data/postgres.nomad.hcl"
  submit_job "${ROOT_DIR}/infra/nomad/jobs/data/valkey.nomad.hcl"
  submit_job "${ROOT_DIR}/infra/nomad/jobs/data/rabbitmq.nomad.hcl"
}

deploy_all_jobs() {
  submit_job "${ROOT_DIR}/infra/nomad/jobs/observability/loki.nomad.hcl"
  submit_job "${ROOT_DIR}/infra/nomad/jobs/observability/tempo.nomad.hcl"
  submit_job "${ROOT_DIR}/infra/nomad/jobs/observability/prometheus.nomad.hcl"
  submit_job "${ROOT_DIR}/infra/nomad/jobs/observability/promtail.nomad.hcl"
  submit_job "${ROOT_DIR}/infra/nomad/jobs/observability/grafana.nomad.hcl"

  submit_job "${ROOT_DIR}/infra/nomad/jobs/platform/n8n.nomad.hcl"
  submit_job "${ROOT_DIR}/infra/nomad/jobs/platform/uptime-kuma.nomad.hcl"
  submit_job "${ROOT_DIR}/infra/nomad/jobs/mail/stalwart.nomad.hcl"

  submit_job "${ROOT_DIR}/infra/nomad/jobs/apps/auth-api.nomad.hcl" -var "image_tag=${IMAGE_TAG}" -var "image_repo=${IMAGE_REPO}"
  submit_job "${ROOT_DIR}/infra/nomad/jobs/apps/assistant-api.nomad.hcl" -var "image_tag=${IMAGE_TAG}" -var "image_repo=${IMAGE_REPO}"
  submit_job "${ROOT_DIR}/infra/nomad/jobs/apps/auth-ui.nomad.hcl" -var "image_tag=${IMAGE_TAG}" -var "image_repo=${IMAGE_REPO}"
  submit_job "${ROOT_DIR}/infra/nomad/jobs/apps/assistant-ui.nomad.hcl" -var "image_tag=${IMAGE_TAG}" -var "image_repo=${IMAGE_REPO}"
  submit_job "${ROOT_DIR}/infra/nomad/jobs/apps/app-ui.nomad.hcl" -var "image_tag=${IMAGE_TAG}" -var "image_repo=${IMAGE_REPO}"

  submit_job "${ROOT_DIR}/infra/nomad/jobs/edge/traefik.nomad.hcl"
}

scale_service() {
  local service="$1"
  local replicas="$2"
  run docker service scale "${STACK_PREFIX}_${service}=${replicas}"
}

backup_vault() {
  local container
  container="$(find_container vault)"

  run mkdir -p "${BACKUP_DIR}"
  echo "+ docker exec ${container} vault operator raft snapshot save /tmp/swarm-vault.snap"
  if [[ "${MODE}" == "apply" ]]; then
    docker exec "${container}" vault operator raft snapshot save /tmp/swarm-vault.snap
    docker cp "${container}:/tmp/swarm-vault.snap" "${BACKUP_DIR}/vault.snap"
  fi
}

backup_postgres() {
  local container
  container="$(find_container postgres)"

  run mkdir -p "${BACKUP_DIR}"
  echo "+ docker exec ${container} pg_dumpall -U postgres > ${BACKUP_DIR}/postgres.sql"
  if [[ "${MODE}" == "apply" ]]; then
    docker exec "${container}" pg_dumpall -U postgres > "${BACKUP_DIR}/postgres.sql"
  fi
}

backup_traefik_acme() {
  local container
  container="$(find_container traefik)"

  run mkdir -p "${BACKUP_DIR}"
  echo "+ docker cp ${container}:/letsencrypt/acme.json ${BACKUP_DIR}/acme.json"
  if [[ "${MODE}" == "apply" ]]; then
    docker cp "${container}:/letsencrypt/acme.json" "${BACKUP_DIR}/acme.json"
  fi
}

bootstrap_command() {
  require_root
  load_bootstrap_env

  run apt-get update
  run apt-get install -y gpg curl unzip ca-certificates lsb-release jq dnsutils postgresql-client

  install_docker
  run systemctl enable docker
  run systemctl start docker

  if id deploy >/dev/null 2>&1; then
    run usermod -aG docker deploy
  fi

  if [[ ! -f /usr/share/keyrings/hashicorp-archive-keyring.gpg ]]; then
    run curl -fsSL https://apt.releases.hashicorp.com/gpg -o /tmp/hashicorp.gpg
    run gpg --dearmor -o /usr/share/keyrings/hashicorp-archive-keyring.gpg /tmp/hashicorp.gpg
  fi

  if [[ ! -f /etc/apt/sources.list.d/hashicorp.list ]]; then
    if [[ "${MODE}" == "dry-run" ]]; then
      echo "+ write /etc/apt/sources.list.d/hashicorp.list"
    else
      echo "deb [signed-by=/usr/share/keyrings/hashicorp-archive-keyring.gpg] https://apt.releases.hashicorp.com $(lsb_release -cs) main" \
        | tee /etc/apt/sources.list.d/hashicorp.list >/dev/null
    fi
  fi

  run apt-get update
  run apt-get install -y consul nomad vault

  run mkdir -p /etc/consul.d /etc/nomad.d /etc/vault.d
  run mkdir -p /opt/consul /opt/nomad /opt/vault/data
  run mkdir -p /srv/nomad/postgres /srv/nomad/prometheus /srv/nomad/traefik /srv/nomad/valkey
  run mkdir -p /srv/nomad/rabbitmq /srv/nomad/n8n /srv/nomad/grafana /srv/nomad/loki
  run mkdir -p /srv/nomad/tempo /srv/nomad/uptime-kuma /srv/nomad/stalwart

  run chown -R consul:consul /opt/consul
  run chown -R nomad:nomad /opt/nomad
  run chown -R vault:vault /opt/vault

  copy_example "${ROOT_DIR}/infra/nomad/configs/consul-server.hcl.example" /etc/consul.d/personal-stack.hcl 0644
  copy_example "${ROOT_DIR}/infra/nomad/configs/nomad-server.hcl.example" /etc/nomad.d/personal-stack.hcl 0644
  copy_example "${ROOT_DIR}/infra/nomad/configs/vault-server.hcl.example" /etc/vault.d/personal-stack.hcl 0644

  cat <<'EOF' | write_text_file /etc/systemd/system/nomad.service.d/override.conf 0644
[Unit]
After=network-online.target docker.service consul.service vault.service
Wants=network-online.target docker.service consul.service vault.service
EOF

  run systemctl daemon-reload
  run systemctl enable consul vault nomad
  run systemctl restart consul
  run systemctl restart vault

  wait_for_systemd consul
  wait_for_systemd vault
  wait_for_http "${VAULT_ADDR_DEFAULT}/v1/sys/health" "Vault API" 90

  run systemctl restart nomad
  wait_for_systemd nomad
  wait_for_nomad_api

  docker_login_ghcr

  echo "Control plane bootstrap complete."
}

init_vault_command() {
  require_command vault
  require_command jq
  load_bootstrap_env
  load_vault_context

  wait_for_http "${VAULT_ADDR}/v1/sys/health" "Vault API" 90

  if [[ ! -f "${VAULT_KEYS_FILE}" ]]; then
    local init_response unseal_key root_token

    if vault status -format=json 2>/dev/null | jq -e '.initialized == true' >/dev/null 2>&1; then
      echo "Vault is already initialized but ${VAULT_KEYS_FILE} is missing. Restore that file or set VAULT_TOKEN manually." >&2
      exit 1
    fi

    echo "+ vault operator init -format=json"
    init_response="$(vault operator init -format=json)"
    unseal_key="$(printf '%s' "${init_response}" | jq -r '.unseal_keys_b64[0]')"
    root_token="$(printf '%s' "${init_response}" | jq -r '.root_token')"

    cat <<EOF | write_text_file "${VAULT_KEYS_FILE}" 0600
VAULT_UNSEAL_KEY=$(shell_single_quote "${unseal_key}")
VAULT_ROOT_TOKEN=$(shell_single_quote "${root_token}")
EOF
  fi

  # shellcheck source=/dev/null
  source "${VAULT_KEYS_FILE}"
  export VAULT_TOKEN="${VAULT_ROOT_TOKEN}"

  if vault status -format=json 2>/dev/null | jq -e '.sealed == true' >/dev/null 2>&1; then
    echo "+ vault operator unseal"
    if [[ "${MODE}" == "apply" ]]; then
      vault operator unseal "${VAULT_UNSEAL_KEY}" >/dev/null
    fi
  fi

  if [[ "${MODE}" == "apply" ]]; then
    vault secrets enable -path=secret -version=2 kv 2>/dev/null || true
  else
    echo "+ vault secrets enable -path=secret -version=2 kv"
  fi

  echo "Vault initialized and unsealed."
}

init_nomad_acl_command() {
  require_command nomad
  require_command jq
  load_nomad_context

  wait_for_nomad_api

  if [[ -f "${NOMAD_KEYS_FILE}" ]]; then
    # shellcheck source=/dev/null
    source "${NOMAD_KEYS_FILE}"
    export NOMAD_TOKEN="${NOMAD_BOOTSTRAP_TOKEN:-}"
    return
  fi

  local bootstrap_response bootstrap_token
  echo "+ nomad acl bootstrap -json"
  if ! bootstrap_response="$(nomad acl bootstrap -json 2>/dev/null)"; then
    echo "Nomad ACL appears to be bootstrapped already, but ${NOMAD_KEYS_FILE} is missing. Restore that file or set NOMAD_TOKEN manually." >&2
    exit 1
  fi
  bootstrap_token="$(printf '%s' "${bootstrap_response}" | jq -r '.SecretID')"

  cat <<EOF | write_text_file "${NOMAD_KEYS_FILE}" 0600
NOMAD_BOOTSTRAP_TOKEN=$(shell_single_quote "${bootstrap_token}")
EOF

  export NOMAD_TOKEN="${bootstrap_token}"
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

  if [[ -z "${N8N_OAUTH_CLIENT_SECRET:-}" ]]; then
    N8N_OAUTH_CLIENT_SECRET="$(random_secret)"
    ensure_bootstrap_env_line N8N_OAUTH_CLIENT_SECRET "${N8N_OAUTH_CLIENT_SECRET}"
  fi

  if [[ -z "${GRAFANA_OAUTH_CLIENT_SECRET:-}" ]]; then
    GRAFANA_OAUTH_CLIENT_SECRET="$(random_secret)"
    ensure_bootstrap_env_line GRAFANA_OAUTH_CLIENT_SECRET "${GRAFANA_OAUTH_CLIENT_SECRET}"
  fi

  if [[ -z "${VAULT_OIDC_CLIENT_SECRET:-}" ]]; then
    VAULT_OIDC_CLIENT_SECRET="$(random_secret)"
    ensure_bootstrap_env_line VAULT_OIDC_CLIENT_SECRET "${VAULT_OIDC_CLIENT_SECRET}"
  fi

  if [[ -z "${STALWART_OAUTH_CLIENT_SECRET:-}" ]]; then
    STALWART_OAUTH_CLIENT_SECRET="$(random_secret)"
    ensure_bootstrap_env_line STALWART_OAUTH_CLIENT_SECRET "${STALWART_OAUTH_CLIENT_SECRET}"
  fi

  if [[ "${MODE}" == "dry-run" ]]; then
    echo "+ seed secret/auth-api, secret/assistant-api, and secret/platform/* from ${BOOTSTRAP_ENV_FILE}"
    return
  fi

  upsert_kv secret/auth-api \
    "spring.rabbitmq.username=${RABBITMQ_USER}" \
    "spring.rabbitmq.password=${RABBITMQ_PASSWORD}" \
    "auth.clients.grafana.secret=${GRAFANA_OAUTH_CLIENT_SECRET}" \
    "auth.clients.n8n.secret=${N8N_OAUTH_CLIENT_SECRET}" \
    "auth.clients.vault.secret=${VAULT_OIDC_CLIENT_SECRET}" \
    "auth.clients.stalwart.secret=${STALWART_OAUTH_CLIENT_SECRET}"

  upsert_kv secret/assistant-api \
    "spring.rabbitmq.username=${RABBITMQ_USER}" \
    "spring.rabbitmq.password=${RABBITMQ_PASSWORD}"

  vault kv put secret/platform/postgres \
    "postgres.user=${POSTGRES_USER}" \
    "postgres.password=${POSTGRES_PASSWORD}" \
    "auth.user=${AUTH_DB_USER}" \
    "auth.password=${AUTH_DB_PASSWORD}" \
    "assistant.user=${ASSISTANT_DB_USER}" \
    "assistant.password=${ASSISTANT_DB_PASSWORD}" >/dev/null

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

  echo "Bootstrap secrets seeded into Vault KV."
}

validate_command() {
  require_command nomad
  require_command consul
  require_command python3
  load_nomad_context

  while IFS= read -r file; do
    echo "+ nomad fmt -check ${file}"
    nomad fmt -check "${file}"

    echo "+ nomad job validate ${file}"
    nomad job validate "${file}"
  done < <(find "${ROOT_DIR}/infra/nomad/jobs" -type f -name "*.hcl" | sort)

  while IFS= read -r file; do
    echo "+ consul validate -config-format=hcl ${file}"
    consul validate -config-format=hcl "${file}"
  done < <(find "${ROOT_DIR}/infra/nomad/configs" -type f -name "consul-*.hcl.example" | sort)

  echo "+ bash -n ${ROOT_DIR}/infra/scripts/migrate-to-nomad.sh"
  bash -n "${ROOT_DIR}/infra/scripts/migrate-to-nomad.sh"

  while IFS= read -r file; do
    echo "+ python3 -m json.tool ${file}"
    python3 -m json.tool "${file}" >/dev/null
  done < <(find "${ROOT_DIR}/infra/nomad/vault" -type f -name "*.json" | sort)
}

prepare_vault_command() {
  load_bootstrap_env
  load_vault_context

  : "${VAULT_ADDR:?Set VAULT_ADDR}"
  : "${VAULT_TOKEN:?Set VAULT_TOKEN}"

  if [[ "${MODE}" == "dry-run" ]]; then
    echo "+ vault secrets enable -path=secret -version=2 kv"
    echo "+ vault auth enable -path=jwt-nomad jwt"
    echo "+ vault secrets enable database"
    echo "+ vault secrets enable transit"
    echo "+ vault write auth/jwt-nomad/config jwks_url=${NOMAD_JWKS_URL} jwt_supported_algs=[RS256,EdDSA] default_role=nomad-workloads"
    echo "+ ensure transit key auth-api-jwt"
    echo "+ configure database secrets engine from secret/platform/postgres against ${DB_ENGINE_HOST}:${DB_ENGINE_PORT}"
    echo "+ write Nomad Vault policies and roles from ${VAULT_DIR}"
    echo "+ configure Vault OIDC auth against ${AUTH_ISSUER}"
    return
  fi

  export VAULT_ADDR
  export VAULT_TOKEN

  vault secrets enable -path=secret -version=2 kv 2>/dev/null || true
  vault auth enable -path=jwt-nomad jwt 2>/dev/null || true
  vault secrets enable database 2>/dev/null || true
  vault secrets enable transit 2>/dev/null || true

  local auth_config_file
  auth_config_file="$(mktemp)"
  cat >"${auth_config_file}" <<EOF
{
  "jwks_url": "${NOMAD_JWKS_URL}",
  "jwt_supported_algs": ["RS256", "EdDSA"],
  "default_role": "nomad-workloads"
}
EOF

  echo "+ vault write auth/jwt-nomad/config @${auth_config_file}"
  vault write auth/jwt-nomad/config @"${auth_config_file}" >/dev/null
  rm -f "${auth_config_file}"

  ensure_transit_key

  if [[ -f "${ROOT_DIR}/infra/vault/policies/admin.hcl" ]]; then
    write_policy admin "${ROOT_DIR}/infra/vault/policies/admin.hcl"
  fi

  while IFS= read -r policy_file; do
    write_policy "$(basename "${policy_file}" .hcl)" "${policy_file}"
  done < <(find "${VAULT_DIR}/policies" -type f -name "*.hcl" | sort)

  while IFS= read -r role_file; do
    write_role "$(basename "${role_file}" .json)" "${role_file}"
  done < <(find "${VAULT_DIR}/roles" -type f -name "*.json" | sort)

  configure_database_engine
  configure_vault_oidc_auth

  echo "Vault is prepared for Nomad workload identity."
}

sync_secrets_command() {
  load_vault_context

  : "${VAULT_ADDR:?Set VAULT_ADDR}"
  : "${VAULT_TOKEN:?Set VAULT_TOKEN}"

  if [[ "${MODE}" == "dry-run" ]]; then
    STACK_PREFIX="${STACK_PREFIX:-$(detect_stack_prefix_from_containers)}"
    echo "Detected Swarm stack prefix: ${STACK_PREFIX}"
    echo "+ read Swarm-mounted secrets from running containers"
    echo "+ patch secret/auth-api and secret/assistant-api with downstream application secrets"
    echo "+ write Nomad KV material under secret/platform/*"
    return
  fi

  export VAULT_ADDR
  export VAULT_TOKEN

  local postgres_user postgres_password auth_db_user auth_db_password assistant_db_user assistant_db_password
  local n8n_db_user n8n_db_password grafana_admin_user grafana_admin_password
  local rabbitmq_user rabbitmq_password cf_dns_api_token stalwart_admin_user
  local stalwart_admin_password n8n_oauth_secret grafana_oauth_secret
  local vault_oauth_secret stalwart_oauth_secret

  STACK_PREFIX="${STACK_PREFIX:-$(detect_stack_prefix_from_containers)}"
  echo "Detected Swarm stack prefix: ${STACK_PREFIX}"

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
  [[ -n "${cf_dns_api_token}" ]] || {
    echo "Could not read cf_dns_api_token from the running Swarm stack." >&2
    exit 1
  }

  n8n_oauth_secret="$(vault_field_or_default secret/auth-api "auth.clients.n8n.secret")"
  [[ -n "${n8n_oauth_secret}" ]] || n8n_oauth_secret="$(random_secret)"

  grafana_oauth_secret="$(vault_field_or_default secret/auth-api "auth.clients.grafana.secret")"
  [[ -n "${grafana_oauth_secret}" ]] || grafana_oauth_secret="$(random_secret)"

  vault_oauth_secret="$(vault_field_or_default secret/auth-api "auth.clients.vault.secret")"
  [[ -n "${vault_oauth_secret}" ]] || vault_oauth_secret="$(random_secret)"

  stalwart_oauth_secret="$(vault_field_or_default secret/auth-api "auth.clients.stalwart.secret")"
  [[ -n "${stalwart_oauth_secret}" ]] || stalwart_oauth_secret="$(random_secret)"

  stalwart_admin_user="$(read_secret_or_default stalwart /run/secrets/stalwart_admin_user admin)"
  stalwart_admin_password="$(read_secret_or_default stalwart /run/secrets/stalwart_admin_password)"
  [[ -n "${stalwart_admin_password}" ]] || stalwart_admin_password="$(random_secret)"

  upsert_kv secret/auth-api \
    "spring.rabbitmq.username=${rabbitmq_user}" \
    "spring.rabbitmq.password=${rabbitmq_password}" \
    "auth.clients.grafana.secret=${grafana_oauth_secret}" \
    "auth.clients.n8n.secret=${n8n_oauth_secret}" \
    "auth.clients.vault.secret=${vault_oauth_secret}" \
    "auth.clients.stalwart.secret=${stalwart_oauth_secret}"

  upsert_kv secret/assistant-api \
    "spring.rabbitmq.username=${rabbitmq_user}" \
    "spring.rabbitmq.password=${rabbitmq_password}"

  vault kv put secret/platform/postgres \
    "postgres.user=${postgres_user}" \
    "postgres.password=${postgres_password}" \
    "auth.user=${auth_db_user}" \
    "auth.password=${auth_db_password}" \
    "assistant.user=${assistant_db_user}" \
    "assistant.password=${assistant_db_password}" >/dev/null

  vault kv put secret/platform/rabbitmq \
    "rabbitmq.user=${rabbitmq_user}" \
    "rabbitmq.password=${rabbitmq_password}" >/dev/null

  vault kv put secret/platform/edge \
    "cloudflare.dns_api_token=${cf_dns_api_token}" >/dev/null

  vault kv put secret/platform/automation \
    "n8n.db_user=${n8n_db_user}" \
    "n8n.db_password=${n8n_db_password}" \
    "n8n.oauth_client_secret=${n8n_oauth_secret}" >/dev/null

  vault kv put secret/platform/observability \
    "grafana.admin_user=${grafana_admin_user}" \
    "grafana.admin_password=${grafana_admin_password}" \
    "grafana.oauth_client_secret=${grafana_oauth_secret}" >/dev/null

  vault kv put secret/platform/mail \
    "stalwart.admin_user=${stalwart_admin_user}" \
    "stalwart.admin_password=${stalwart_admin_password}" \
    "stalwart.oauth_client_secret=${stalwart_oauth_secret}" >/dev/null

  echo "Swarm secrets synced into Vault KV for Nomad consumers."
}

deploy_command() {
  validate_command
  deploy_data_jobs

  if [[ "${PHASE}" == "data" ]]; then
    return
  fi

  if [[ "${PHASE}" != "all" ]]; then
    echo "Unsupported phase: ${PHASE}" >&2
    exit 1
  fi

  deploy_all_jobs
}

bootstrap_server_command() {
  require_root

  bootstrap_command
  require_command jq
  require_command curl
  require_command nomad
  require_command vault
  init_vault_command
  init_nomad_acl_command
  seed_secrets_command
  prepare_vault_command

  local original_phase="${PHASE}"
  PHASE="data"
  deploy_command
  PHASE="${original_phase}"

  wait_for_job_running postgres 240
  wait_for_postgres 240
  wait_for_job_running rabbitmq 180

  prepare_vault_command
  deploy_command

  wait_for_job_running traefik 180
  wait_for_job_running auth-api 240
  wait_for_job_running assistant-api 240
  wait_for_job_running app-ui 240

  echo "Clean server bootstrap complete."
  echo "Credentials written to:"
  echo "  ${BOOTSTRAP_ENV_FILE}"
  echo "  ${VAULT_KEYS_FILE}"
  echo "  ${NOMAD_KEYS_FILE}"
}

cutover_command() {
  STACK_PREFIX="${STACK_PREFIX:-$(detect_stack_prefix_from_services)}"
  echo "Scaling down Swarm stack: ${STACK_PREFIX}"

  scale_service traefik 0
  scale_service app-ui 0
  scale_service auth-ui 0
  scale_service assistant-ui 0
  scale_service auth-api 0
  scale_service assistant-api 0

  echo "Run the system tests against the Nomad environment immediately after cutover."
}

rollback_command() {
  STACK_PREFIX="${STACK_PREFIX:-$(detect_stack_prefix_from_services)}"
  echo "Scaling Swarm stack back up: ${STACK_PREFIX}"

  scale_service traefik 1
  scale_service app-ui 2
  scale_service auth-ui 2
  scale_service assistant-ui 2
  scale_service auth-api 2
  scale_service assistant-api 2

  echo "After rollback, revert DNS or your load balancer target to the Swarm host."
}

migrate_command() {
  require_command docker
  require_command nomad
  require_command vault
  require_command openssl

  load_nomad_context
  load_vault_context

  STACK_PREFIX="${STACK_PREFIX:-$(detect_stack_prefix_from_containers)}"
  echo "Detected Swarm stack prefix: ${STACK_PREFIX}"

  backup_vault
  backup_postgres
  backup_traefik_acme
  sync_secrets_command
  prepare_vault_command

  local original_phase="${PHASE}"
  PHASE="data"
  deploy_command
  PHASE="${original_phase}"

  prepare_vault_command
  deploy_command

  echo "Migration preparation complete."
  echo "Next:"
  echo "  1. Restore ${BACKUP_DIR}/postgres.sql into the Nomad-managed PostgreSQL instance if you are migrating to a new host."
  echo "  2. Restore ${BACKUP_DIR}/vault.snap into the target Vault instance if required."
  echo "  3. Validate Nomad job health with 'nomad status'."
  echo "  4. Run infra/scripts/migrate-to-nomad.sh cutover once the Nomad stack is ready."
}

main() {
  local command="${1:-}"
  [[ -n "${command}" ]] || {
    usage
    exit 1
  }
  shift || true

  parse_common_flags "$@"

  case "${command}" in
    bootstrap)
      bootstrap_command
      ;;
    init-vault)
      init_vault_command
      ;;
    seed-secrets)
      seed_secrets_command
      ;;
    bootstrap-server)
      bootstrap_server_command
      ;;
    validate)
      validate_command
      ;;
    prepare-vault)
      prepare_vault_command
      ;;
    sync-secrets)
      sync_secrets_command
      ;;
    deploy)
      deploy_command
      ;;
    cutover)
      cutover_command
      ;;
    rollback)
      rollback_command
      ;;
    migrate)
      migrate_command
      ;;
    *)
      echo "Unknown command: ${command}" >&2
      usage
      exit 1
      ;;
  esac
}

main "$@"
