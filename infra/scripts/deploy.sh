#!/usr/bin/env bash
# deploy.sh — Submit Nomad jobs for zero-downtime rolling updates.
#
# Usage:
#   deploy.sh [--phase data|infra|edge|apps|all] [--wait] [--dry-run]
#
# Phases:
#   data   PostgreSQL, Valkey, RabbitMQ
#   infra  Observability, platform, mail, core jobs (no Traefik)
#   edge   Traefik only — run after cutover so Swarm no longer holds ports 80/443
#   apps   auth-api, assistant-api, auth-ui, assistant-ui, app-ui
#   all    Everything (default)
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STACK_DIR="${STACK_DIR:-/opt/personal-stack}"
BOOTSTRAP_ENV_FILE="${BOOTSTRAP_ENV_FILE:-${STACK_DIR}/.nomad-bootstrap.env}"
VAULT_KEYS_FILE="${VAULT_KEYS_FILE:-${STACK_DIR}/.vault-keys}"
NOMAD_KEYS_FILE="${NOMAD_KEYS_FILE:-${STACK_DIR}/.nomad-keys}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
IMAGE_REPO="${IMAGE_REPO:-ghcr.io/extratoast/personal-stack}"
DOMAIN="${DOMAIN:-jorisjonkers.dev}"
REPO_DIR_VAR="${REPO_DIR:-/opt/personal-stack}"

MODE="apply"
PHASE="all"
WAIT=false

# ── Helpers ────────────────────────────────────────────────────────────────

usage() {
  cat <<'EOF'
Usage: deploy.sh [--phase data|infra|apps|all] [--wait] [--dry-run]

Phases:
  data   PostgreSQL, Valkey, RabbitMQ
  infra  Observability, platform, mail, edge, core jobs
  apps   auth-api, assistant-api, auth-ui, assistant-ui, app-ui
  all    Everything (default)

Options:
  --wait      Block until critical jobs are running
  --dry-run   Print commands without executing
EOF
}

run() {
  echo "+ $*"
  [[ "${MODE}" == "apply" ]] && "$@"
}

load_context() {
  if [[ -f "${BOOTSTRAP_ENV_FILE}" ]]; then
    set -a; source "${BOOTSTRAP_ENV_FILE}"; set +a
  fi

  if [[ -f "${NOMAD_KEYS_FILE}" ]]; then
    source "${NOMAD_KEYS_FILE}"
    export NOMAD_TOKEN="${NOMAD_TOKEN:-${NOMAD_BOOTSTRAP_TOKEN:-}}"
  fi

  if [[ -f "${VAULT_KEYS_FILE}" ]]; then
    source "${VAULT_KEYS_FILE}"
    export VAULT_TOKEN="${VAULT_TOKEN:-${VAULT_ROOT_TOKEN:-}}"
  fi

  export VAULT_ADDR="${VAULT_ADDR:-http://127.0.0.1:8200}"
  export NOMAD_ADDR="${NOMAD_ADDR:-http://127.0.0.1:4646}"
}

vault_is_unsealed() {
  curl -fsS "${VAULT_ADDR}/v1/sys/seal-status" | jq -e '.sealed == false' >/dev/null 2>&1
}

ensure_vault_unsealed() {
  if ! vault_is_unsealed; then
    if [[ -n "${VAULT_UNSEAL_KEY:-}" ]]; then
      echo "+ vault operator unseal"
      vault operator unseal "${VAULT_UNSEAL_KEY}" >/dev/null
    else
      echo "Vault is sealed and no unseal key is available." >&2
      exit 1
    fi
  fi
}

submit_job() {
  local file="$1"; shift
  run nomad job run "$@" "${file}"
}

wait_for_job_running() {
  local job="$1" timeout="${2:-240}" elapsed=0
  while (( elapsed < timeout )); do
    if nomad job allocs -json "${job}" 2>/dev/null \
        | jq -e 'length > 0 and all(.[]; .ClientStatus == "running")' >/dev/null 2>&1; then
      return 0
    fi
    sleep 3; elapsed=$((elapsed + 3))
  done
  echo "Timed out waiting for job ${job}." >&2
  nomad job status "${job}" || true
  exit 1
}

# ── Parse arguments ────────────────────────────────────────────────────────

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run) MODE="dry-run" ;;
    --wait)    WAIT=true ;;
    --phase)   PHASE="${2:?Missing value for --phase}"; shift ;;
    -h|--help) usage; exit 0 ;;
    *)         echo "Unknown argument: $1" >&2; usage; exit 1 ;;
  esac
  shift
done

# ── Main ───────────────────────────────────────────────────────────────────

# Nomad HCL files use file() with paths relative to the project root
cd "${ROOT_DIR}"

load_context
ensure_vault_unsealed

JOBS_DIR="${ROOT_DIR}/infra/nomad/jobs"
DOMAIN_VAR=(-var "domain=${DOMAIN}")
REPO_VAR=(-var "repo_dir=${REPO_DIR_VAR}")
APP_VARS=(-var "image_tag=${IMAGE_TAG}" -var "image_repo=${IMAGE_REPO}")
EXTRA_VARS=()  # additional vars passed via NOMAD_EXTRA_VARS env (e.g. "-var count=1 -var tls_mode=file")
if [[ -n "${NOMAD_EXTRA_VARS:-}" ]]; then
  read -ra EXTRA_VARS <<< "${NOMAD_EXTRA_VARS}"
fi

deploy_data() {
  submit_job "${JOBS_DIR}/data/postgres.nomad.hcl"  "${REPO_VAR[@]}"
  submit_job "${JOBS_DIR}/data/valkey.nomad.hcl"
  submit_job "${JOBS_DIR}/data/rabbitmq.nomad.hcl"  "${DOMAIN_VAR[@]}" "${REPO_VAR[@]}"
}

seed_stalwart_mail_account() {
  local stalwart_addr password="${STALWART_MAIL_PASSWORD:-}"
  [[ -n "${password}" ]] || { echo "STALWART_MAIL_PASSWORD not set, skipping mail account seed."; return 0; }

  echo "+ Waiting for stalwart to be ready"
  wait_for_job_running stalwart 120

  stalwart_addr="$(nomad service info -json stalwart 2>/dev/null | jq -r '.[0] | .Address + ":" + (.Port | tostring)')" || true
  [[ -n "${stalwart_addr}" && "${stalwart_addr}" != "null:null" ]] || { echo "Could not resolve stalwart address, skipping account seed."; return 0; }

  local admin_user="${STALWART_ADMIN_USER:-admin}"
  local admin_pass="${STALWART_ADMIN_PASSWORD:-}"

  echo "+ Seeding stalwart mail account 'auth'"
  curl -sf -u "${admin_user}:${admin_pass}" \
    "http://${stalwart_addr}/api/principal" \
    -H "Content-Type: application/json" \
    -d '{
      "type": "individual",
      "name": "auth",
      "secrets": ["'"${password}"'"],
      "emails": ["auth@'"${DOMAIN}"'"]
    }' && echo " done" || echo " (may already exist)"
}

deploy_infra() {
  submit_job "${JOBS_DIR}/observability/loki.nomad.hcl"      "${REPO_VAR[@]}"
  submit_job "${JOBS_DIR}/observability/tempo.nomad.hcl"
  submit_job "${JOBS_DIR}/observability/prometheus.nomad.hcl"
  submit_job "${JOBS_DIR}/observability/promtail.nomad.hcl"
  submit_job "${JOBS_DIR}/observability/grafana.nomad.hcl"   "${DOMAIN_VAR[@]}" "${REPO_VAR[@]}"
  submit_job "${JOBS_DIR}/platform/n8n.nomad.hcl"            "${DOMAIN_VAR[@]}" "${REPO_VAR[@]}"
  submit_job "${JOBS_DIR}/platform/uptime-kuma.nomad.hcl"    "${DOMAIN_VAR[@]}"
  submit_job "${JOBS_DIR}/mail/stalwart.nomad.hcl"           "${DOMAIN_VAR[@]}"
  seed_stalwart_mail_account
}

deploy_edge() {
  submit_job "${JOBS_DIR}/edge/traefik.nomad.hcl" "${DOMAIN_VAR[@]}" "${EXTRA_VARS[@]}"
}

deploy_apps() {
  submit_job "${JOBS_DIR}/apps/auth-api.nomad.hcl"     "${DOMAIN_VAR[@]}" "${APP_VARS[@]}" "${EXTRA_VARS[@]}"
  submit_job "${JOBS_DIR}/apps/assistant-api.nomad.hcl" "${DOMAIN_VAR[@]}" "${APP_VARS[@]}" "${EXTRA_VARS[@]}"
  submit_job "${JOBS_DIR}/apps/auth-ui.nomad.hcl"       "${DOMAIN_VAR[@]}" "${APP_VARS[@]}" "${EXTRA_VARS[@]}"
  submit_job "${JOBS_DIR}/apps/assistant-ui.nomad.hcl"   "${DOMAIN_VAR[@]}" "${APP_VARS[@]}" "${EXTRA_VARS[@]}"
  submit_job "${JOBS_DIR}/apps/app-ui.nomad.hcl"         "${DOMAIN_VAR[@]}" "${APP_VARS[@]}" "${EXTRA_VARS[@]}"
}

case "${PHASE}" in
  data)  deploy_data ;;
  infra) deploy_infra ;;
  edge)  deploy_edge ;;
  apps)  deploy_apps ;;
  all)   deploy_data; deploy_infra; deploy_apps; deploy_edge ;;
  *)     echo "Unknown phase: ${PHASE}" >&2; exit 1 ;;
esac

if [[ "${WAIT}" == true && "${MODE}" == "apply" ]]; then
  echo "Waiting for critical jobs..."
  case "${PHASE}" in
    data)
      wait_for_job_running postgres 240
      wait_for_job_running rabbitmq 180
      ;;
    apps)
      wait_for_job_running auth-api 300
      wait_for_job_running assistant-api 300
      ;;
    all|infra)
      wait_for_job_running auth-api 300
      wait_for_job_running assistant-api 300
      ;;
  esac
  echo "All critical jobs running."
fi
