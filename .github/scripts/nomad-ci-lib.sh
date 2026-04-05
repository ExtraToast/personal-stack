#!/usr/bin/env bash
# Shared helpers for CI-only Nomad bootstrap and diagnostics.

CI_NOMAD_LOG_TAIL_LINES="${CI_NOMAD_LOG_TAIL_LINES:-160}"
CI_NOMAD_JOBS=(
  postgres
  valkey
  rabbitmq
  traefik
  auth-api
  assistant-api
  auth-ui
  assistant-ui
  app-ui
  n8n
  grafana
  stalwart
)

wait_for_http_endpoint() {
  local url="$1" description="$2" attempts="${3:-30}" delay="${4:-2}"
  local attempt
  for attempt in $(seq 1 "${attempts}"); do
    if curl -sS -o /dev/null "${url}" >/dev/null 2>&1; then
      echo "  ${description} reachable (attempt ${attempt})"
      return 0
    fi
    sleep "${delay}"
  done

  echo "  ERROR: ${description} not reachable after $((attempts * delay))s" >&2
  return 1
}

resolve_nomad_service_address() {
  local service="$1" attempts="${2:-15}" delay="${3:-2}"
  local attempt service_json address

  for attempt in $(seq 1 "${attempts}"); do
    service_json="$(nomad service info -json "${service}" 2>/dev/null || true)"
    address="$(printf '%s' "${service_json}" \
      | jq -r '.[0]? | select(.Address != null and .Port != null) | .Address + ":" + (.Port | tostring)' \
        2>/dev/null || true)"

    if [[ -n "${address}" && "${address}" != "null:null" ]]; then
      echo "${address}"
      return 0
    fi

    sleep "${delay}"
  done

  return 1
}

read_vault_status_json() {
  curl -fsS "${VAULT_ADDR}/v1/sys/seal-status"
}

handoff_ci_state_files() {
  local owner="${SUDO_USER:-}"
  local file
  [[ -n "${owner}" ]] || return 0
  for file in "$@"; do
    [[ -f "${file}" ]] || continue
    chown "${owner}" "${file}" 2>/dev/null || true
    chmod 0600 "${file}" 2>/dev/null || true
  done
}

load_nomad_ci_context() {
  if [[ -f "${NOMAD_KEYS_FILE:-}" ]]; then
    # shellcheck disable=SC1090
    source "${NOMAD_KEYS_FILE}"
    export NOMAD_TOKEN="${NOMAD_TOKEN:-${NOMAD_BOOTSTRAP_TOKEN:-}}"
  fi

  if [[ -f "${VAULT_KEYS_FILE:-}" ]]; then
    # shellcheck disable=SC1090
    source "${VAULT_KEYS_FILE}"
    export VAULT_TOKEN="${VAULT_TOKEN:-${VAULT_ROOT_TOKEN:-}}"
    export VAULT_UNSEAL_KEY="${VAULT_UNSEAL_KEY:-}"
  fi
}

ensure_vault_unsealed() {
  load_nomad_ci_context

  if [[ ! -f "${VAULT_KEYS_FILE:-}" ]]; then
    echo "  WARN: ${VAULT_KEYS_FILE:-.vault-keys} not found, skipping unseal check"
    return 0
  fi

  local status sealed initialized
  if ! wait_for_http_endpoint "${VAULT_ADDR}/v1/sys/health" "Vault API" 15 2; then
    systemctl status vault --no-pager || true
    journalctl -u vault --no-pager -n 60 || true
    return 1
  fi

  if ! status="$(read_vault_status_json)"; then
    echo "  ERROR: Cannot determine Vault status" >&2
    vault status 2>&1 || true
    return 1
  fi

  sealed="$(printf '%s' "${status}" | jq -r 'if has("sealed") then .sealed else "unknown" end')"
  initialized="$(printf '%s' "${status}" | jq -r 'if has("initialized") then .initialized else "unknown" end')"

  echo "  Vault status: initialized=${initialized} sealed=${sealed}"

  if [[ "${initialized}" != "true" ]]; then
    echo "  ERROR: Vault is reachable but not initialized" >&2
    return 1
  fi

  if [[ "${sealed}" == "true" ]]; then
    if [[ -z "${VAULT_UNSEAL_KEY:-}" ]]; then
      echo "  ERROR: Vault is sealed and no unseal key is available" >&2
      return 1
    fi
    echo "  Unsealing Vault..."
    vault operator unseal "${VAULT_UNSEAL_KEY}" >/dev/null
    echo "  Vault unsealed."
  elif [[ "${sealed}" == "unknown" ]]; then
    echo "  ERROR: Cannot determine Vault seal status" >&2
    vault status 2>&1 || true
    return 1
  fi
}

dump_systemd_service_diagnostics() {
  local service="$1"
  echo "========== systemd:${service} =========="
  systemctl status "${service}" --no-pager 2>&1 || true
  journalctl -u "${service}" --no-pager -n 200 2>&1 || true
}

dump_nomad_alloc_diagnostics() {
  local alloc="$1" task task_names
  echo "--- allocation ${alloc} status ---"
  nomad alloc status "${alloc}" 2>&1 || true

  task_names="$(nomad alloc status -json "${alloc}" 2>/dev/null | jq -r '.TaskStates | keys[]?' 2>/dev/null || true)"
  if [[ -z "${task_names}" ]]; then
    echo "--- allocation ${alloc} logs ---"
    nomad alloc logs "${alloc}" 2>&1 | tail -n "${CI_NOMAD_LOG_TAIL_LINES}" || true
    return
  fi

  while IFS= read -r task; do
    [[ -n "${task}" ]] || continue
    echo "--- allocation ${alloc} task ${task} stderr ---"
    nomad alloc logs -stderr -task "${task}" "${alloc}" 2>&1 | tail -n "${CI_NOMAD_LOG_TAIL_LINES}" || true
    echo "--- allocation ${alloc} task ${task} stdout ---"
    nomad alloc logs -task "${task}" "${alloc}" 2>&1 | tail -n "${CI_NOMAD_LOG_TAIL_LINES}" || true
  done <<< "${task_names}"
}

dump_nomad_job_diagnostics() {
  local job="$1" deployment alloc
  echo "========== job:${job} =========="
  nomad job status "${job}" 2>&1 || true

  while IFS= read -r deployment; do
    [[ -n "${deployment}" ]] || continue
    echo "--- deployment ${deployment} ---"
    nomad deployment status "${deployment}" 2>&1 || true
  done < <(nomad job deployments -json "${job}" 2>/dev/null | jq -r '.[].ID' 2>/dev/null || true)

  while IFS= read -r alloc; do
    [[ -n "${alloc}" ]] || continue
    dump_nomad_alloc_diagnostics "${alloc}"
  done < <(nomad job allocs -json "${job}" 2>/dev/null | jq -r '.[].ID' 2>/dev/null || true)
}

dump_ci_diagnostics() {
  local service job
  set +e

  load_nomad_ci_context || true

  echo "==> CI diagnostics"
  date -u 2>/dev/null || true
  echo ""

  echo "==> Docker containers"
  docker ps -a 2>&1 || true
  echo ""

  for service in consul vault nomad; do
    dump_systemd_service_diagnostics "${service}"
    echo ""
  done

  echo "==> Nomad cluster status"
  nomad status 2>&1 || true
  echo ""

  for job in "${CI_NOMAD_JOBS[@]}"; do
    dump_nomad_job_diagnostics "${job}"
    echo ""
  done
}

submit_nomad_job() {
  local file="$1"
  shift
  echo "+ nomad job run -detach $* ${file}"
  nomad job run -detach "$@" "${file}"
}

wait_for_nomad_job_ready() {
  local job="$1" timeout="${2:-240}" elapsed=0
  local last_progress="" allocs_json live_allocs_json deployments_json deployment_id deployment_json
  local deployment_status deployment_description desired_total placed_total healthy_total unhealthy_total
  local total_allocs running_allocs progress

  while (( elapsed < timeout )); do
    allocs_json="$(nomad job allocs -json "${job}" 2>/dev/null || printf '[]')"
    live_allocs_json="$(printf '%s' "${allocs_json}" \
      | jq '[.[] | select((.DesiredStatus // "run") == "run")]')"
    total_allocs="$(printf '%s' "${live_allocs_json}" | jq -r 'length')"
    running_allocs="$(printf '%s' "${live_allocs_json}" | jq -r '[.[] | select(.ClientStatus == "running")] | length')"

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

      if (( desired_total > 0 && healthy_total >= desired_total )) \
        && (( running_allocs >= desired_total )); then
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

    sleep 3
    elapsed=$((elapsed + 3))
  done

  echo "Timed out waiting for job ${job} after ${timeout}s." >&2
  dump_nomad_job_diagnostics "${job}"
  return 1
}

wait_for_nomad_jobs() {
  while (( $# > 0 )); do
    wait_for_nomad_job_ready "$1" "$2" || return 1
    shift 2
  done
}
