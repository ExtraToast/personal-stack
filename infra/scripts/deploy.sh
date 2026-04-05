#!/usr/bin/env bash
# deploy.sh — Submit Nomad jobs for zero-downtime rolling updates.
#
# Usage:
#   deploy.sh [--phase data|infra|edge|apps|all] [--wait] [--dry-run]
#
# Phases:
#   data   PostgreSQL, Valkey, RabbitMQ
#   infra  Observability, platform, mail, core jobs (no Traefik)
#   edge   Traefik only
#   apps   auth-api, assistant-api, auth-ui, assistant-ui, app-ui
#   all    Everything (default)
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
IMAGE_TAG_OVERRIDE="${IMAGE_TAG-}"
IMAGE_REPO_OVERRIDE="${IMAGE_REPO-}"
DOMAIN_OVERRIDE="${DOMAIN-}"
REPO_DIR_OVERRIDE="${REPO_DIR-}"
APP_COUNT_OVERRIDE="${APP_COUNT-}"
IMAGE_TAG="${IMAGE_TAG_OVERRIDE:-latest}"
IMAGE_REPO="${IMAGE_REPO_OVERRIDE:-ghcr.io/extratoast/personal-stack}"
DOMAIN="${DOMAIN_OVERRIDE:-jorisjonkers.dev}"
REPO_DIR_VAR="${REPO_DIR_OVERRIDE:-/opt/personal-stack}"
APP_COUNT="${APP_COUNT_OVERRIDE:-1}"

MODE="apply"
PHASE="all"
WAIT=false

# ── Helpers ────────────────────────────────────────────────────────────────

usage() {
  cat <<'EOF'
Usage: deploy.sh [--phase data|infra|edge|apps|all] [--wait] [--dry-run]

Phases:
  data   PostgreSQL, Valkey, RabbitMQ
  infra  Observability, platform, mail, core jobs
  edge   Traefik only
  apps   auth-api, assistant-api, auth-ui, assistant-ui, app-ui
  all    Everything (default)

Options:
  --wait      Block until critical jobs are running
  --dry-run   Print commands without executing

Environment:
  NOMAD_TOKEN  Required for apply mode
  NOMAD_ADDR   Optional, defaults to http://127.0.0.1:4646
  DOMAIN       Optional, defaults to jorisjonkers.dev
  IMAGE_REPO   Optional, defaults to ghcr.io/extratoast/personal-stack
  IMAGE_TAG    Optional, defaults to latest
  REPO_DIR     Optional, defaults to /opt/personal-stack
  APP_COUNT    Optional, defaults to 1 for single-node capacity
EOF
}

run() {
  echo "+ $*"
  [[ "${MODE}" == "apply" ]] && "$@"
}

restore_deploy_overrides() {
  if [[ -n "${IMAGE_TAG_OVERRIDE:-}" ]]; then
    export IMAGE_TAG="${IMAGE_TAG_OVERRIDE}"
  fi
  if [[ -n "${IMAGE_REPO_OVERRIDE:-}" ]]; then
    export IMAGE_REPO="${IMAGE_REPO_OVERRIDE}"
  fi
  if [[ -n "${DOMAIN_OVERRIDE:-}" ]]; then
    export DOMAIN="${DOMAIN_OVERRIDE}"
  fi
  if [[ -n "${REPO_DIR_OVERRIDE:-}" ]]; then
    export REPO_DIR="${REPO_DIR_OVERRIDE}"
    REPO_DIR_VAR="${REPO_DIR_OVERRIDE}"
  fi
  if [[ -n "${APP_COUNT_OVERRIDE:-}" ]]; then
    export APP_COUNT="${APP_COUNT_OVERRIDE}"
  fi
}

load_context() {
  restore_deploy_overrides
  export NOMAD_ADDR="${NOMAD_ADDR:-http://127.0.0.1:4646}"
  if [[ "${MODE}" == "apply" ]]; then
    : "${NOMAD_TOKEN:?Set NOMAD_TOKEN}"
  fi
}

submit_job() {
  local file="$1"; shift
  run nomad job run -detach "$@" "${file}"
}

dump_job_diagnostics() {
  local job="$1" alloc task

  nomad job status "${job}" || true

  alloc="$(nomad job allocs -json "${job}" 2>/dev/null | jq -r 'sort_by(.CreateTime) | reverse | .[0].ID // empty')" || true
  [[ -n "${alloc}" ]] || return 0

  echo "+ nomad alloc status ${alloc}"
  nomad alloc status "${alloc}" || true

  task="$(nomad alloc status -json "${alloc}" 2>/dev/null | jq -r '.TaskStates | keys[0] // empty')" || true
  [[ -n "${task}" ]] || return 0

  echo "+ nomad alloc logs -stderr ${alloc} ${task}"
  nomad alloc logs -stderr "${alloc}" "${task}" || true
  echo "+ nomad alloc logs ${alloc} ${task}"
  nomad alloc logs "${alloc}" "${task}" || true
}

wait_for_job_ready() {
  local job="$1" timeout="${2:-240}" elapsed=0
  local last_progress="" allocs_json live_allocs_json deployments_json deployment_id deployment_json
  local deployment_status deployment_description desired_total placed_total healthy_total unhealthy_total
  local total_allocs running_allocs progress

  while (( elapsed < timeout )); do
    allocs_json="$(nomad job allocs -json "${job}" 2>/dev/null || printf '[]')"
    live_allocs_json="$(printf '%s' "${allocs_json}" \
      | jq '[.[] | select((.DesiredStatus // "run") == "run")]')"
    total_allocs="$(printf '%s' "${live_allocs_json}" | jq -r 'length')"
    running_allocs="$(printf '%s' "${live_allocs_json}" \
      | jq -r '[.[] | select(.ClientStatus == "running")] | length')"

    deployments_json="$(nomad job deployments -json "${job}" 2>/dev/null || printf '[]')"
    deployment_id="$(printf '%s' "${deployments_json}" \
      | jq -r 'map(select((.Status // "") != "cancelled")) | sort_by(.ModifyTime // .CreateTime // 0) | last | .ID // empty')"

    if [[ -n "${deployment_id}" ]]; then
      deployment_json="$(nomad deployment status -json "${deployment_id}" 2>/dev/null || printf '{}')"
      deployment_status="$(printf '%s' "${deployment_json}" | jq -r '.Status // "unknown"')"
      deployment_description="$(printf '%s' "${deployment_json}" | jq -r '.StatusDescription // empty')"
      desired_total="$(printf '%s' "${deployment_json}" | jq -r '[.TaskGroups[]?.DesiredTotal // 0] | add // 0')"
      placed_total="$(printf '%s' "${deployment_json}" | jq -r '[.TaskGroups[]?.PlacedAllocs // 0] | add // 0')"
      healthy_total="$(printf '%s' "${deployment_json}" | jq -r '[.TaskGroups[]?.HealthyAllocs // 0] | add // 0')"
      unhealthy_total="$(printf '%s' "${deployment_json}" | jq -r '[.TaskGroups[]?.UnhealthyAllocs // 0] | add // 0')"

      progress="status=${deployment_status} placed=${placed_total} desired=${desired_total} healthy=${healthy_total} unhealthy=${unhealthy_total} allocs=${running_allocs}/${total_allocs}"
      if [[ "${progress}" != "${last_progress}" ]]; then
        echo "  ${job}: ${progress}${deployment_description:+ (${deployment_description})}"
        last_progress="${progress}"
      fi

      if (( desired_total > 0 && healthy_total >= desired_total && running_allocs >= desired_total )); then
        return 0
      fi
    else
      progress="allocs=${running_allocs}/${total_allocs}"
      if [[ "${progress}" != "${last_progress}" ]]; then
        echo "  ${job}: ${progress}"
        last_progress="${progress}"
      fi

      if (( total_allocs > 0 )) && printf '%s' "${live_allocs_json}" \
        | jq -e 'all(.[]; .ClientStatus == "running")' >/dev/null 2>&1; then
        return 0
      fi
    fi

    sleep 3; elapsed=$((elapsed + 3))
  done
  echo "Timed out waiting for job ${job}." >&2
  dump_job_diagnostics "${job}"
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

echo "+ deploy context: phase=${PHASE} domain=${DOMAIN} image_repo=${IMAGE_REPO} image_tag=${IMAGE_TAG} app_count=${APP_COUNT}"

JOBS_DIR="${ROOT_DIR}/infra/nomad/jobs"
DOMAIN_VAR=(-var "domain=${DOMAIN}")
REPO_VAR=(-var "repo_dir=${REPO_DIR_VAR}")
APP_VARS=(-var "image_tag=${IMAGE_TAG}" -var "image_repo=${IMAGE_REPO}")
EXTRA_VARS=()  # additional vars passed via NOMAD_EXTRA_VARS env (e.g. "-var count=1 -var tls_mode=file")
if [[ -n "${NOMAD_EXTRA_VARS:-}" ]]; then
  read -ra EXTRA_VARS <<< "${NOMAD_EXTRA_VARS}"
fi
APP_COUNT_VAR=(-var "count=${APP_COUNT}")
for token in "${EXTRA_VARS[@]}"; do
  if [[ "${token}" == count=* ]]; then
    APP_COUNT_VAR=()
    break
  fi
done

deploy_data() {
  submit_job "${JOBS_DIR}/data/postgres.nomad.hcl"  "${REPO_VAR[@]}"
  submit_job "${JOBS_DIR}/data/valkey.nomad.hcl"
  submit_job "${JOBS_DIR}/data/rabbitmq.nomad.hcl"  "${DOMAIN_VAR[@]}" "${REPO_VAR[@]}"
}

deploy_infra() {
  submit_job "${JOBS_DIR}/observability/loki.nomad.hcl"      "${REPO_VAR[@]}"
  submit_job "${JOBS_DIR}/observability/tempo.nomad.hcl"
  submit_job "${JOBS_DIR}/observability/prometheus.nomad.hcl"
  submit_job "${JOBS_DIR}/observability/promtail.nomad.hcl"
  submit_job "${JOBS_DIR}/observability/grafana.nomad.hcl"   "${DOMAIN_VAR[@]}" "${REPO_VAR[@]}"
  submit_job "${JOBS_DIR}/platform/n8n.nomad.hcl"            "${DOMAIN_VAR[@]}" "${REPO_VAR[@]}"
  submit_job "${JOBS_DIR}/platform/flaresolverr.nomad.hcl"
  submit_job "${JOBS_DIR}/platform/uptime-kuma.nomad.hcl"    "${DOMAIN_VAR[@]}"
  submit_job "${JOBS_DIR}/mail/stalwart.nomad.hcl"           "${DOMAIN_VAR[@]}"
}

deploy_edge() {
  submit_job "${JOBS_DIR}/edge/traefik.nomad.hcl" "${DOMAIN_VAR[@]}" "${EXTRA_VARS[@]}"
}

deploy_apps() {
  submit_job "${JOBS_DIR}/apps/auth-api.nomad.hcl"      "${DOMAIN_VAR[@]}" "${APP_COUNT_VAR[@]}" "${APP_VARS[@]}" "${EXTRA_VARS[@]}"
  submit_job "${JOBS_DIR}/apps/assistant-api.nomad.hcl" "${DOMAIN_VAR[@]}" "${APP_COUNT_VAR[@]}" "${APP_VARS[@]}" "${EXTRA_VARS[@]}"
  submit_job "${JOBS_DIR}/apps/auth-ui.nomad.hcl"       "${DOMAIN_VAR[@]}" "${APP_COUNT_VAR[@]}" "${APP_VARS[@]}" "${EXTRA_VARS[@]}"
  submit_job "${JOBS_DIR}/apps/assistant-ui.nomad.hcl"  "${DOMAIN_VAR[@]}" "${APP_COUNT_VAR[@]}" "${APP_VARS[@]}" "${EXTRA_VARS[@]}"
  submit_job "${JOBS_DIR}/apps/app-ui.nomad.hcl"        "${DOMAIN_VAR[@]}" "${APP_COUNT_VAR[@]}" "${APP_VARS[@]}" "${EXTRA_VARS[@]}"
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
      wait_for_job_ready postgres 240
      wait_for_job_ready valkey 180
      wait_for_job_ready rabbitmq 180
      ;;
    infra)
      wait_for_job_ready grafana 300
      wait_for_job_ready n8n 300
      wait_for_job_ready flaresolverr 180
      wait_for_job_ready stalwart 300
      wait_for_job_ready uptime-kuma 240
      ;;
    edge)
      wait_for_job_ready traefik 180
      ;;
    apps)
      wait_for_job_ready auth-api 300
      wait_for_job_ready assistant-api 300
      wait_for_job_ready auth-ui 240
      wait_for_job_ready assistant-ui 240
      wait_for_job_ready app-ui 240
      ;;
    all)
      wait_for_job_ready postgres 240
      wait_for_job_ready valkey 180
      wait_for_job_ready rabbitmq 180
      wait_for_job_ready grafana 300
      wait_for_job_ready n8n 300
      wait_for_job_ready flaresolverr 180
      wait_for_job_ready stalwart 300
      wait_for_job_ready uptime-kuma 240
      wait_for_job_ready auth-api 300
      wait_for_job_ready assistant-api 300
      wait_for_job_ready auth-ui 240
      wait_for_job_ready assistant-ui 240
      wait_for_job_ready app-ui 240
      wait_for_job_ready traefik 180
      ;;
  esac
  echo "All critical jobs running."
fi
