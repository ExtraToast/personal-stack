#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
NOMAD_DIR="${ROOT_DIR}/infra/nomad"
VAULT_DIR="${NOMAD_DIR}/vault"
MODE="apply"
PHASE="all"
BACKUP_DIR="${BACKUP_DIR:-${ROOT_DIR}/tmp/nomad-migration}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
IMAGE_REPO="${IMAGE_REPO:-ghcr.io/extratoast/personal-stack}"
NOMAD_JWKS_URL="${NOMAD_JWKS_URL:-https://nomad.jorisjonkers.dev/v1/jwks}"
NOMAD_ISSUER="${NOMAD_ISSUER:-https://nomad.jorisjonkers.dev}"
DB_ENGINE_HOST="${DB_ENGINE_HOST:-127.0.0.1}"
DB_ENGINE_PORT="${DB_ENGINE_PORT:-5432}"
STACK_PREFIX="${STACK_PREFIX:-}"

usage() {
  cat <<'EOF'
Usage:
  infra/scripts/migrate-to-nomad.sh <command> [options]

Commands:
  bootstrap       Install Nomad, Consul, and Vault prerequisites on the target host
  validate        Validate Nomad jobs, Consul configs, JSON roles, and this script
  prepare-vault   Configure Vault JWT auth, workload policies/roles, Transit, and DB engine
  sync-secrets    Copy live Swarm secrets into Vault KV for Nomad consumers
  deploy          Submit Nomad jobs
  cutover         Scale down Swarm ingress and app services after Nomad is ready
  rollback        Scale Swarm ingress and app services back up
  migrate         Backup Swarm state, sync secrets, prepare Vault, deploy data, re-prepare Vault, deploy all jobs

Options:
  --dry-run       Print commands without executing them
  --phase <name>  For deploy: data | all (default: all)
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
  local postgres_user postgres_password

  if vault read database/config/postgres >/dev/null 2>&1 &&
    vault read database/roles/auth-api >/dev/null 2>&1 &&
    vault read database/roles/assistant-api >/dev/null 2>&1; then
    echo "Vault database secrets engine is already configured."
    return
  fi

  postgres_user="$(vault kv get -field=postgres.user secret/platform/postgres 2>/dev/null || true)"
  postgres_password="$(vault kv get -field=postgres.password secret/platform/postgres 2>/dev/null || true)"

  if [[ -z "${postgres_user}" || -z "${postgres_password}" ]]; then
    echo "Skipping database secrets engine setup: secret/platform/postgres is not populated yet."
    return
  fi

  echo "+ vault write database/config/postgres ..."
  vault write database/config/postgres \
    plugin_name=postgresql-database-plugin \
    "connection_url=postgresql://{{username}}:{{password}}@${DB_ENGINE_HOST}:${DB_ENGINE_PORT}/postgres?sslmode=disable" \
    allowed_roles="auth-api,assistant-api" \
    "username=${postgres_user}" \
    "password=${postgres_password}" >/dev/null

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
      REASSIGN OWNED BY \"{{name}}\" TO auth_user;
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
      REASSIGN OWNED BY \"{{name}}\" TO assistant_user;
      DROP OWNED BY \"{{name}}\";
      DROP ROLE IF EXISTS \"{{name}}\";" \
    default_ttl=1h \
    max_ttl=24h >/dev/null
}

submit_job() {
  local file="$1"
  shift || true
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

  run apt-get update
  run apt-get install -y gpg curl unzip ca-certificates lsb-release jq dnsutils

  if [[ ! -f /usr/share/keyrings/hashicorp-archive-keyring.gpg ]]; then
    run curl -fsSL https://apt.releases.hashicorp.com/gpg -o /tmp/hashicorp.gpg
    run gpg --dearmor -o /usr/share/keyrings/hashicorp-archive-keyring.gpg /tmp/hashicorp.gpg
  fi

  if [[ ! -f /etc/apt/sources.list.d/hashicorp.list ]]; then
    echo "deb [signed-by=/usr/share/keyrings/hashicorp-archive-keyring.gpg] https://apt.releases.hashicorp.com $(lsb_release -cs) main" \
      | tee /etc/apt/sources.list.d/hashicorp.list >/dev/null
  fi

  run apt-get update
  run apt-get install -y consul nomad vault

  run mkdir -p /etc/consul.d /etc/nomad.d /etc/vault.d
  run mkdir -p /srv/nomad/postgres
  run mkdir -p /srv/nomad/prometheus
  run mkdir -p /srv/nomad/traefik
  run mkdir -p /srv/nomad/valkey
  run mkdir -p /srv/nomad/rabbitmq
  run mkdir -p /srv/nomad/n8n
  run mkdir -p /srv/nomad/grafana
  run mkdir -p /srv/nomad/loki
  run mkdir -p /srv/nomad/tempo
  run mkdir -p /srv/nomad/uptime-kuma
  run mkdir -p /srv/nomad/stalwart

  echo "Control plane bootstrap complete."
}

validate_command() {
  require_command nomad
  require_command consul
  require_command python3

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
  : "${VAULT_ADDR:?Set VAULT_ADDR}"
  : "${VAULT_TOKEN:?Set VAULT_TOKEN}"

  if [[ "${MODE}" == "dry-run" ]]; then
    echo "+ vault auth enable -path=jwt-nomad jwt"
    echo "+ vault secrets enable database"
    echo "+ vault secrets enable transit"
    echo "+ vault write auth/jwt-nomad/config oidc_discovery_url=${NOMAD_ISSUER} bound_issuer=${NOMAD_ISSUER} jwks_url=${NOMAD_JWKS_URL}"
    echo "+ ensure transit key auth-api-jwt"
    echo "+ configure database secrets engine from secret/platform/postgres against ${DB_ENGINE_HOST}:${DB_ENGINE_PORT}"
    echo "+ write Nomad Vault policies and roles from ${VAULT_DIR}"
    return
  fi

  export VAULT_ADDR
  export VAULT_TOKEN

  vault auth enable -path=jwt-nomad jwt 2>/dev/null || true
  vault secrets enable database 2>/dev/null || true
  vault secrets enable transit 2>/dev/null || true

  echo "+ vault write auth/jwt-nomad/config ..."
  vault write auth/jwt-nomad/config \
    oidc_discovery_url="${NOMAD_ISSUER}" \
    bound_issuer="${NOMAD_ISSUER}" \
    jwks_url="${NOMAD_JWKS_URL}" \
    default_role="auth-api"

  ensure_transit_key
  configure_database_engine

  while IFS= read -r policy_file; do
    write_policy "$(basename "${policy_file}" .hcl)" "${policy_file}"
  done < <(find "${VAULT_DIR}/policies" -type f -name "*.hcl" | sort)

  while IFS= read -r role_file; do
    write_role "$(basename "${role_file}" .json)" "${role_file}"
  done < <(find "${VAULT_DIR}/roles" -type f -name "*.json" | sort)

  echo "Vault is prepared for Nomad workload identity."
}

sync_secrets_command() {
  : "${VAULT_ADDR:?Set VAULT_ADDR}"
  : "${VAULT_TOKEN:?Set VAULT_TOKEN}"

  if [[ "${MODE}" == "dry-run" ]]; then
    STACK_PREFIX="${STACK_PREFIX:-$(detect_stack_prefix_from_containers)}"
    echo "Detected Swarm stack prefix: ${STACK_PREFIX}"
    echo "+ read Swarm-mounted secrets from running containers"
    echo "+ patch secret/auth-api with downstream OAuth client secrets"
    echo "+ write Nomad KV material under secret/platform/*"
    return
  fi

  export VAULT_ADDR
  export VAULT_TOKEN

  local postgres_user postgres_password auth_db_password assistant_db_password
  local n8n_db_user n8n_db_password grafana_admin_user grafana_admin_password
  local rabbitmq_user rabbitmq_password cf_dns_api_token stalwart_admin_user
  local stalwart_admin_password n8n_oauth_secret grafana_oauth_secret
  local vault_oauth_secret stalwart_oauth_secret

  STACK_PREFIX="${STACK_PREFIX:-$(detect_stack_prefix_from_containers)}"
  echo "Detected Swarm stack prefix: ${STACK_PREFIX}"

  postgres_user="$(read_secret_or_default postgres /run/secrets/postgres_user postgres)"
  postgres_password="$(read_secret_or_default postgres /run/secrets/postgres_password)"
  auth_db_password="$(read_secret_or_default postgres /run/secrets/auth_db_password auth_password)"
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

  echo "+ vault kv patch secret/auth-api ..."
  upsert_kv secret/auth-api \
    "auth.clients.grafana.secret=${grafana_oauth_secret}" \
    "auth.clients.n8n.secret=${n8n_oauth_secret}" \
    "auth.clients.vault.secret=${vault_oauth_secret}" \
    "auth.clients.stalwart.secret=${stalwart_oauth_secret}"

  echo "+ vault kv put secret/platform/postgres ..."
  vault kv put secret/platform/postgres \
    "postgres.user=${postgres_user}" \
    "postgres.password=${postgres_password}" \
    "auth.password=${auth_db_password}" \
    "assistant.password=${assistant_db_password}" >/dev/null

  echo "+ vault kv put secret/platform/rabbitmq ..."
  vault kv put secret/platform/rabbitmq \
    "rabbitmq.user=${rabbitmq_user}" \
    "rabbitmq.password=${rabbitmq_password}" >/dev/null

  echo "+ vault kv put secret/platform/edge ..."
  vault kv put secret/platform/edge \
    "cloudflare.dns_api_token=${cf_dns_api_token}" >/dev/null

  echo "+ vault kv put secret/platform/automation ..."
  vault kv put secret/platform/automation \
    "n8n.db_user=${n8n_db_user}" \
    "n8n.db_password=${n8n_db_password}" \
    "n8n.oauth_client_secret=${n8n_oauth_secret}" >/dev/null

  echo "+ vault kv put secret/platform/observability ..."
  vault kv put secret/platform/observability \
    "grafana.admin_user=${grafana_admin_user}" \
    "grafana.admin_password=${grafana_admin_password}" \
    "grafana.oauth_client_secret=${grafana_oauth_secret}" >/dev/null

  echo "+ vault kv put secret/platform/mail ..."
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
