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

# ── Helper: ensure Vault is unsealed ──────────────────────────────────────

ensure_vault_unsealed() {
  if [[ -f "${VAULT_KEYS_FILE}" ]]; then
    source "${VAULT_KEYS_FILE}"
    export VAULT_TOKEN="${VAULT_ROOT_TOKEN:-}"
    export VAULT_UNSEAL_KEY="${VAULT_UNSEAL_KEY:-}"
  else
    echo "  WARN: ${VAULT_KEYS_FILE} not found, skipping unseal check"
    return 0
  fi

  # Wait for Vault HTTP to be reachable (may be starting up)
  local attempt
  for attempt in $(seq 1 15); do
    if vault status -format=json >/dev/null 2>&1; then break; fi
    echo "  Waiting for Vault API... (attempt ${attempt}/15)"
    sleep 2
  done

  local status
  status="$(vault status -format=json 2>/dev/null || echo '{}')"
  local sealed
  sealed="$(echo "${status}" | jq -r '.sealed // "unknown"')"
  local initialized
  initialized="$(echo "${status}" | jq -r '.initialized // "unknown"')"

  echo "  Vault status: initialized=${initialized} sealed=${sealed}"

  if [[ "${sealed}" == "true" ]]; then
    echo "  Unsealing Vault..."
    vault operator unseal "${VAULT_UNSEAL_KEY}" >/dev/null
    echo "  Vault unsealed."
  elif [[ "${sealed}" == "unknown" ]]; then
    echo "  ERROR: Cannot determine Vault status — API may not be reachable"
    vault status 2>&1 || true
    return 1
  fi
}

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

# ── Fix CI-specific issues ─────────────────────────────────────────────────

# Disable UFW — configure re-enables it and it blocks internal traffic
echo "==> Disabling UFW (not needed in CI)"
ufw disable 2>/dev/null || true

# Consul fails with systemd timeout because the bind_addr is the runner's
# private IP and Consul's notify-based readiness check doesn't complete in time.
# Override to 127.0.0.1 — all CI traffic is on localhost with host networking.
echo "==> Overriding Consul bind_addr to 127.0.0.1 for CI"
sed -i 's/bind_addr.*/bind_addr = "127.0.0.1"/' /etc/consul.d/consul.hcl

# Restart all services now that UFW is disabled and Consul bind is fixed
echo "==> Restarting services..."
for svc in consul vault nomad; do
  systemctl restart "${svc}" || true
  sleep 2
done

# Verify services are running
echo "==> Verifying services..."
for svc in consul vault nomad; do
  if systemctl is-active --quiet "${svc}"; then
    echo "  ${svc}: running"
  else
    echo "  ${svc}: FAILED"
    journalctl -u "${svc}" --no-pager -n 30 || true
    exit 1
  fi
done

# Wait for Consul API
echo "==> Waiting for Consul API..."
for attempt in $(seq 1 30); do
  if curl -sf http://127.0.0.1:8500/v1/status/leader >/dev/null 2>&1; then
    echo "  Consul API reachable (attempt ${attempt})"
    break
  fi
  if [[ "${attempt}" -eq 30 ]]; then
    echo "  ERROR: Consul API not reachable after 60s"
    systemctl status consul --no-pager || true
    journalctl -u consul --no-pager -n 30 || true
    exit 1
  fi
  sleep 2
done

# Wait for Vault API
echo "==> Waiting for Vault API..."
for attempt in $(seq 1 30); do
  if vault status >/dev/null 2>&1; then
    echo "  Vault API reachable (attempt ${attempt})"
    break
  fi
  if [[ "${attempt}" -eq 30 ]]; then
    echo "  ERROR: Vault API not reachable after 60s"
    systemctl status vault --no-pager || true
    journalctl -u vault --no-pager -n 30 || true
    exit 1
  fi
  sleep 2
done

# Wait for Nomad API
echo "==> Waiting for Nomad API..."
for attempt in $(seq 1 30); do
  if curl -sf http://127.0.0.1:4646/v1/status/leader >/dev/null 2>&1; then
    echo "  Nomad API reachable (attempt ${attempt})"
    break
  fi
  if [[ "${attempt}" -eq 30 ]]; then
    echo "  ERROR: Nomad API not reachable after 60s"
    systemctl status nomad --no-pager || true
    journalctl -u nomad --no-pager -n 30 || true
    exit 1
  fi
  sleep 2
done

# ── Initialize Vault ──────────────────────────────────────────────────────

echo "==> Initializing Vault"
bash "${SCRIPT_DIR}/setup.sh" init-vault

# Verify unseal worked
echo "==> Verifying Vault is unsealed after init"
ensure_vault_unsealed

# ── Initialize Nomad ACL ──────────────────────────────────────────────────

echo "==> Initializing Nomad ACL"
bash "${SCRIPT_DIR}/setup.sh" init-nomad

# ── Seed secrets into Vault KV ────────────────────────────────────────────

echo "==> Seeding Vault secrets"
ensure_vault_unsealed
bash "${SCRIPT_DIR}/setup.sh" seed-secrets

# ── Configure Vault engines (first pass — DB/RMQ engines will be skipped) ─

echo "==> Preparing Vault (first pass: JWT, policies, roles, transit)"
ensure_vault_unsealed
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
ensure_vault_unsealed
DOMAIN="${DOMAIN}" REPO_DIR="${ROOT_DIR}" \
  bash "${SCRIPT_DIR}/deploy.sh" --phase data --wait

# ── Re-run prepare-vault (database + rabbitmq engines now possible) ───────

echo "==> Preparing Vault (second pass: database + rabbitmq engines)"
ensure_vault_unsealed
bash "${SCRIPT_DIR}/setup.sh" prepare-vault

# ── Deploy edge (Traefik with file-based TLS) ────────────────────────────

echo "==> Deploying edge (Traefik with self-signed TLS)"
ensure_vault_unsealed
DOMAIN="${DOMAIN}" NOMAD_EXTRA_VARS="-var tls_mode=file -var tls_cert_dir=${CERT_DIR}" \
  bash "${SCRIPT_DIR}/deploy.sh" --phase edge

# ── Deploy apps with count=1 to save CI runner resources ──────────────────

echo "==> Deploying apps (count=1)"
ensure_vault_unsealed
DOMAIN="${DOMAIN}" IMAGE_REPO="${IMAGE_REPO}" IMAGE_TAG="${IMAGE_TAG}" \
  REPO_DIR="${ROOT_DIR}" NOMAD_EXTRA_VARS="-var count=1" \
  bash "${SCRIPT_DIR}/deploy.sh" --phase apps --wait

# ── Re-run prepare-vault (Vault OIDC needs auth-api running) ─────────────

echo "==> Preparing Vault (third pass: OIDC configuration)"
ensure_vault_unsealed
AUTH_ISSUER="https://auth.${DOMAIN}" \
  bash "${SCRIPT_DIR}/setup.sh" prepare-vault

# ── Deploy platform services needed by tests ──────────────────────────────

echo "==> Deploying platform services (n8n, grafana, stalwart)"
ensure_vault_unsealed
source "${NOMAD_KEYS_FILE}"
export NOMAD_TOKEN="${NOMAD_TOKEN:-${NOMAD_BOOTSTRAP_TOKEN:-}}"

JOBS_DIR="${ROOT_DIR}/infra/nomad/jobs"
cd "${ROOT_DIR}"
nomad job run -var "domain=${DOMAIN}" -var "repo_dir=${ROOT_DIR}" "${JOBS_DIR}/observability/grafana.nomad.hcl"
nomad job run -var "domain=${DOMAIN}" -var "repo_dir=${ROOT_DIR}" "${JOBS_DIR}/platform/n8n.nomad.hcl"
nomad job run -var "domain=${DOMAIN}" "${JOBS_DIR}/mail/stalwart.nomad.hcl"

echo "==> Nomad CI stack bootstrap complete"
