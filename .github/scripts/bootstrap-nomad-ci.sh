#!/usr/bin/env bash
# Bootstrap the Nomad/Consul/Vault stack for GitHub Actions system tests.

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
# shellcheck source=.github/scripts/nomad-ci-lib.sh
source "${SCRIPT_DIR}/nomad-ci-lib.sh"

export STACK_DIR="${STACK_DIR:-${ROOT_DIR}}"
export BOOTSTRAP_ENV_FILE="${STACK_DIR}/.nomad-bootstrap.env"
export VAULT_KEYS_FILE="${STACK_DIR}/.vault-keys"
export NOMAD_KEYS_FILE="${STACK_DIR}/.nomad-keys"
export VAULT_ADDR="${VAULT_ADDR:-http://127.0.0.1:8200}"
export NOMAD_ADDR="${NOMAD_ADDR:-http://127.0.0.1:4646}"

DOMAIN="jorisjonkers.test"
IMAGE_REPO="personal-stack"
IMAGE_TAG="ci"
CERT_DIR="/srv/nomad/traefik/certs"
BOOTSTRAP_DIAGNOSTICS_RAN=false

bootstrap_error() {
  local exit_code="$1" line="$2" command="$3"
  if [[ "${BOOTSTRAP_DIAGNOSTICS_RAN}" == "true" ]]; then
    exit "${exit_code}"
  fi

  BOOTSTRAP_DIAGNOSTICS_RAN=true
  echo "==> Bootstrap failed at line ${line}: ${command}" >&2
  dump_ci_diagnostics
  exit "${exit_code}"
}
trap 'bootstrap_error $? $LINENO "$BASH_COMMAND"' ERR

deploy_data_phase() {
  echo "==> Deploying data tier (postgres, valkey, rabbitmq)"
  ensure_vault_unsealed
  submit_nomad_job "${ROOT_DIR}/infra/nomad/jobs/data/postgres.nomad.hcl" -var "repo_dir=${ROOT_DIR}"
  submit_nomad_job "${ROOT_DIR}/infra/nomad/jobs/data/valkey.nomad.hcl"
  submit_nomad_job "${ROOT_DIR}/infra/nomad/jobs/data/rabbitmq.nomad.hcl" -var "domain=${DOMAIN}" -var "repo_dir=${ROOT_DIR}" -var "oidc_tls_skip_verify=true"
  wait_for_nomad_jobs postgres 240 valkey 180 rabbitmq 180
}

deploy_edge_phase() {
  echo "==> Deploying edge (Traefik with self-signed TLS)"
  ensure_vault_unsealed
  submit_nomad_job "${ROOT_DIR}/infra/nomad/jobs/edge/traefik.nomad.hcl" \
    -var "domain=${DOMAIN}" \
    -var "tls_mode=file" \
    -var "tls_cert_dir=${CERT_DIR}"
  wait_for_nomad_jobs traefik 180
}

deploy_apps_phase() {
  echo "==> Deploying apps (count=1)"
  ensure_vault_unsealed
  submit_nomad_job "${ROOT_DIR}/infra/nomad/jobs/apps/auth-api.nomad.hcl" \
    -var "domain=${DOMAIN}" \
    -var "image_tag=${IMAGE_TAG}" \
    -var "image_repo=${IMAGE_REPO}" \
    -var "count=1"
  submit_nomad_job "${ROOT_DIR}/infra/nomad/jobs/apps/assistant-api.nomad.hcl" \
    -var "domain=${DOMAIN}" \
    -var "image_tag=${IMAGE_TAG}" \
    -var "image_repo=${IMAGE_REPO}" \
    -var "count=1"
  submit_nomad_job "${ROOT_DIR}/infra/nomad/jobs/apps/auth-ui.nomad.hcl" \
    -var "domain=${DOMAIN}" \
    -var "image_tag=${IMAGE_TAG}" \
    -var "image_repo=${IMAGE_REPO}" \
    -var "count=1"
  submit_nomad_job "${ROOT_DIR}/infra/nomad/jobs/apps/assistant-ui.nomad.hcl" \
    -var "domain=${DOMAIN}" \
    -var "image_tag=${IMAGE_TAG}" \
    -var "image_repo=${IMAGE_REPO}" \
    -var "count=1"
  submit_nomad_job "${ROOT_DIR}/infra/nomad/jobs/apps/app-ui.nomad.hcl" \
    -var "domain=${DOMAIN}" \
    -var "image_tag=${IMAGE_TAG}" \
    -var "image_repo=${IMAGE_REPO}" \
    -var "count=1"
  wait_for_nomad_jobs auth-api 300 assistant-api 300 auth-ui 240 assistant-ui 240 app-ui 240
}

deploy_platform_phase() {
  echo "==> Deploying platform services (n8n, grafana, stalwart)"
  ensure_vault_unsealed
  submit_nomad_job "${ROOT_DIR}/infra/nomad/jobs/observability/grafana.nomad.hcl" \
    -var "domain=${DOMAIN}" \
    -var "repo_dir=${ROOT_DIR}" \
    -var "oidc_tls_skip_verify=true"
  submit_nomad_job "${ROOT_DIR}/infra/nomad/jobs/platform/n8n.nomad.hcl" \
    -var "domain=${DOMAIN}" \
    -var "repo_dir=${ROOT_DIR}" \
    -var "oidc_ca_cert_path=${CERT_DIR}/wildcard.crt"
  submit_nomad_job "${ROOT_DIR}/infra/nomad/jobs/mail/stalwart.nomad.hcl" \
    -var "domain=${DOMAIN}"
  wait_for_nomad_jobs grafana 300 n8n 300 stalwart 300

  echo "==> Seeding stalwart mail account for auth-api"
  local admin_user="${STALWART_ADMIN_USER:-admin}"
  local admin_pass="${STALWART_ADMIN_PASSWORD:-}"
  local mail_password="${STALWART_MAIL_PASSWORD:-}"
  [[ -n "${mail_password}" ]] || {
    echo "  STALWART_MAIL_PASSWORD not set, skipping stalwart account seed"
    return 0
  }

  local stalwart_addr
  stalwart_addr="$(resolve_nomad_service_address stalwart 10 2 || true)"
  if [[ -z "${stalwart_addr}" || "${stalwart_addr}" == "null:null" ]]; then
    echo "  Stalwart is healthy but not yet in Nomad service discovery; falling back to 127.0.0.1:8080"
    stalwart_addr="127.0.0.1:8080"
  fi
  curl -sf -u "${admin_user}:${admin_pass}" \
    "http://${stalwart_addr}/api/principal" \
    -H "Content-Type: application/json" \
    -d '{
      "type": "individual",
      "name": "auth",
      "secrets": ["'"${mail_password}"'"],
      "emails": ["auth@'"${DOMAIN}"'"]
    }' || echo "Warning: failed to seed stalwart account (may already exist)"
}

echo "==> Writing CI bootstrap secrets"
cat > "${BOOTSTRAP_ENV_FILE}" <<'ENVFILE'
POSTGRES_USER=postgres
POSTGRES_PASSWORD=ci_postgres_pass
AUTH_DB_USER=auth_user
AUTH_DB_PASSWORD=ci_auth_pass
ASSISTANT_DB_USER=assistant_user
ASSISTANT_DB_PASSWORD=ci_assistant_pass
N8N_DB_USER=n8n_user
N8N_DB_PASSWORD=ci_n8n_pass
RABBITMQ_USER=appuser
RABBITMQ_PASSWORD=ci_rabbitmq_pass
CF_DNS_API_TOKEN=ci_dummy_cloudflare_token
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=ci_grafana_pass
STALWART_ADMIN_USER=admin
STALWART_ADMIN_PASSWORD=ci_stalwart_pass
STALWART_MAIL_PASSWORD=ci_stalwart_mail_pass
N8N_OAUTH_CLIENT_SECRET=ci_n8n_oauth_secret
GRAFANA_OAUTH_CLIENT_SECRET=ci_grafana_oauth_secret
VAULT_OIDC_CLIENT_SECRET=ci_vault_oidc_secret
ENVFILE
handoff_ci_state_files "${BOOTSTRAP_ENV_FILE}"
set -a
source "${BOOTSTRAP_ENV_FILE}"
set +a

echo "==> Installing Consul, Nomad, Vault and creating data directories"
bash "${ROOT_DIR}/infra/scripts/setup.sh" install

echo "==> Configuring host (Consul, Vault, Nomad)"
bash "${ROOT_DIR}/infra/scripts/setup.sh" configure

echo "==> Disabling UFW (not needed in CI)"
ufw disable 2>/dev/null || true

echo "==> Overriding Consul bind_addr to 127.0.0.1 for CI"
sed -i 's/bind_addr.*/bind_addr = "127.0.0.1"/' /etc/consul.d/consul.hcl

echo "==> Overriding Consul systemd service type to simple"
mkdir -p /etc/systemd/system/consul.service.d
cat > /etc/systemd/system/consul.service.d/ci-override.conf <<'UNIT'
[Service]
Type=simple
UNIT
systemctl daemon-reload

echo "==> Restarting services..."
for svc in consul vault nomad; do
  systemctl restart "${svc}" || true
  sleep 2
done

echo "==> Verifying services..."
for svc in consul vault nomad; do
  if systemctl is-active --quiet "${svc}"; then
    echo "  ${svc}: running"
  else
    echo "  ${svc}: FAILED" >&2
    dump_systemd_service_diagnostics "${svc}"
    exit 1
  fi
done

echo "==> Waiting for Consul API..."
wait_for_http_endpoint "http://127.0.0.1:8500/v1/status/leader" "Consul API" 30 2

echo "==> Waiting for Vault API..."
wait_for_http_endpoint "${VAULT_ADDR}/v1/sys/health" "Vault API" 30 2

echo "==> Waiting for Nomad API..."
wait_for_http_endpoint "${NOMAD_ADDR}/v1/status/leader" "Nomad API" 30 2

echo "==> Initializing Vault"
bash "${ROOT_DIR}/infra/scripts/setup.sh" init-vault
handoff_ci_state_files "${VAULT_KEYS_FILE}"

echo "==> Verifying Vault is unsealed after init"
ensure_vault_unsealed

echo "==> Initializing Nomad ACL"
bash "${ROOT_DIR}/infra/scripts/setup.sh" init-nomad
handoff_ci_state_files "${NOMAD_KEYS_FILE}"
load_nomad_ci_context

echo "==> Seeding Vault secrets"
ensure_vault_unsealed
bash "${ROOT_DIR}/infra/scripts/setup.sh" seed-secrets

echo "==> Preparing Vault (first pass: JWT, policies, roles, transit)"
ensure_vault_unsealed
bash "${ROOT_DIR}/infra/scripts/setup.sh" prepare-vault

echo "==> Generating self-signed TLS certificates"
bash "${ROOT_DIR}/infra/scripts/generate-dev-tls-cert.sh"
mkdir -p "${CERT_DIR}"
cp "${ROOT_DIR}/infra/traefik/dynamic-dev/certs/jorisjonkers.test.crt" "${CERT_DIR}/wildcard.crt"
cp "${ROOT_DIR}/infra/traefik/dynamic-dev/certs/jorisjonkers.test.key" "${CERT_DIR}/wildcard.key"
cp "${CERT_DIR}/wildcard.crt" /usr/local/share/ca-certificates/jorisjonkers-test.crt
update-ca-certificates

echo "==> Restarting Vault to pick up new CA certificates"
systemctl restart vault
sleep 2
ensure_vault_unsealed

deploy_data_phase

echo "==> Preparing Vault (second pass: database + rabbitmq engines)"
ensure_vault_unsealed
bash "${ROOT_DIR}/infra/scripts/setup.sh" prepare-vault

deploy_edge_phase
deploy_apps_phase

echo "==> Preparing Vault (third pass: OIDC configuration)"
ensure_vault_unsealed
VAULT_PUBLIC_ADDR="https://vault.${DOMAIN}" \
AUTH_ISSUER="https://auth.${DOMAIN}" \
  bash "${ROOT_DIR}/infra/scripts/setup.sh" prepare-vault

deploy_platform_phase

trap - ERR
echo "==> Nomad CI stack bootstrap complete"
