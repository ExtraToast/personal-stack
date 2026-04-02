#!/usr/bin/env bash
# ci-bootstrap-nomad.sh — Bootstrap a full Nomad/Consul/Vault stack on a CI runner.
#
# This script is designed for GitHub Actions ubuntu-latest runners.
# It calls setup.sh commands with CI-appropriate environment variables,
# then deploys all services via deploy.sh.
#
# Prerequisites:
#   - Docker is installed and running (GitHub Actions provides this)
#   - Docker images are loaded into the local daemon (via build-images job)
#   - Dev DNS is set up (via setup-dev-dns.sh)
#
# Usage:
#   sudo bash infra/scripts/ci-bootstrap-nomad.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# ── CI environment ────────────────────────────────────────────────────────

export STACK_DIR="${STACK_DIR:-${ROOT_DIR}}"
export BOOTSTRAP_ENV_FILE="${STACK_DIR}/.nomad-bootstrap.env"
export VAULT_KEYS_FILE="${STACK_DIR}/.vault-keys"
export NOMAD_KEYS_FILE="${STACK_DIR}/.nomad-keys"
export VAULT_ADDR="http://127.0.0.1:8200"
export NOMAD_ADDR="http://127.0.0.1:4646"

DOMAIN="jorisjonkers.test"
IMAGE_REPO="personal-stack"
IMAGE_TAG="latest"

# ── Write bootstrap env with deterministic CI passwords ───────────────────

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
N8N_OAUTH_CLIENT_SECRET=ci_n8n_oauth_secret
GRAFANA_OAUTH_CLIENT_SECRET=ci_grafana_oauth_secret
VAULT_OIDC_CLIENT_SECRET=ci_vault_oidc_secret
STALWART_OAUTH_CLIENT_SECRET=ci_stalwart_oauth_secret
ENVFILE

# ── Install HashiCorp tools and create data directories ───────────────────

echo "==> Installing Consul, Nomad, Vault and creating data directories"
bash "${SCRIPT_DIR}/setup.sh" install

# ── Configure and start services ──────────────────────────────────────────

echo "==> Configuring host (Consul, Vault, Nomad)"
bash "${SCRIPT_DIR}/setup.sh" configure

# ── Initialize Vault ──────────────────────────────────────────────────────

echo "==> Initializing Vault"
bash "${SCRIPT_DIR}/setup.sh" init-vault

# ── Initialize Nomad ACL ──────────────────────────────────────────────────

echo "==> Initializing Nomad ACL"
bash "${SCRIPT_DIR}/setup.sh" init-nomad

# ── Seed secrets into Vault KV ────────────────────────────────────────────

echo "==> Seeding Vault secrets"
bash "${SCRIPT_DIR}/setup.sh" seed-secrets

# ── Configure Vault engines (first pass — DB/RMQ engines will be skipped) ─

echo "==> Preparing Vault (first pass: JWT, policies, roles, transit)"
bash "${SCRIPT_DIR}/setup.sh" prepare-vault

# ── Generate self-signed TLS certs for Traefik ────────────────────────────

echo "==> Generating self-signed TLS certificates"
bash "${SCRIPT_DIR}/generate-dev-tls-cert.sh"

CERT_DIR="/srv/nomad/traefik/certs"
mkdir -p "${CERT_DIR}"
cp "${ROOT_DIR}/infra/traefik/dynamic-dev/certs/jorisjonkers.test.crt" "${CERT_DIR}/wildcard.crt"
cp "${ROOT_DIR}/infra/traefik/dynamic-dev/certs/jorisjonkers.test.key" "${CERT_DIR}/wildcard.key"

# Trust the self-signed CA so Vault OIDC discovery works
cp "${CERT_DIR}/wildcard.crt" /usr/local/share/ca-certificates/jorisjonkers-test.crt
update-ca-certificates

# ── Deploy data tier ──────────────────────────────────────────────────────

echo "==> Deploying data tier (postgres, valkey, rabbitmq)"
DOMAIN="${DOMAIN}" REPO_DIR="${ROOT_DIR}" \
  bash "${SCRIPT_DIR}/deploy.sh" --phase data --wait

# ── Re-run prepare-vault (database + rabbitmq engines now possible) ───────

echo "==> Preparing Vault (second pass: database + rabbitmq engines)"
bash "${SCRIPT_DIR}/setup.sh" prepare-vault

# ── Deploy edge (Traefik with file-based TLS) ────────────────────────────

echo "==> Deploying edge (Traefik with self-signed TLS)"
DOMAIN="${DOMAIN}" NOMAD_EXTRA_VARS="-var tls_mode=file -var tls_cert_dir=${CERT_DIR}" \
  bash "${SCRIPT_DIR}/deploy.sh" --phase edge

# ── Deploy apps with count=1 to save CI runner resources ──────────────────

echo "==> Deploying apps (count=1)"
DOMAIN="${DOMAIN}" IMAGE_REPO="${IMAGE_REPO}" IMAGE_TAG="${IMAGE_TAG}" \
  REPO_DIR="${ROOT_DIR}" NOMAD_EXTRA_VARS="-var count=1" \
  bash "${SCRIPT_DIR}/deploy.sh" --phase apps --wait

# ── Re-run prepare-vault (Vault OIDC needs auth-api running) ─────────────

echo "==> Preparing Vault (third pass: OIDC configuration)"
AUTH_ISSUER="https://auth.${DOMAIN}" \
  bash "${SCRIPT_DIR}/setup.sh" prepare-vault

# ── Deploy platform services needed by tests ──────────────────────────────

echo "==> Deploying platform services (n8n, grafana, stalwart)"
source "${NOMAD_KEYS_FILE}"
export NOMAD_TOKEN="${NOMAD_TOKEN:-${NOMAD_BOOTSTRAP_TOKEN:-}}"
source "${VAULT_KEYS_FILE}"
export VAULT_TOKEN="${VAULT_TOKEN:-${VAULT_ROOT_TOKEN:-}}"

JOBS_DIR="${ROOT_DIR}/infra/nomad/jobs"
cd "${ROOT_DIR}"
nomad job run -var "domain=${DOMAIN}" -var "repo_dir=${ROOT_DIR}" "${JOBS_DIR}/observability/grafana.nomad.hcl"
nomad job run -var "domain=${DOMAIN}" -var "repo_dir=${ROOT_DIR}" "${JOBS_DIR}/platform/n8n.nomad.hcl"
nomad job run -var "domain=${DOMAIN}" "${JOBS_DIR}/mail/stalwart.nomad.hcl"

echo "==> Nomad CI stack bootstrap complete"
