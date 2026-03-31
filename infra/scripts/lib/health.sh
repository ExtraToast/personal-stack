#!/usr/bin/env bash
# health.sh — Post-rotation health verification.
#
# Checks that all critical services are healthy after credential rotation.

verify_rotation() {
  local all_healthy=true

  info "Running post-rotation health checks..."

  # ── Check Vault is unsealed ─────────────────────────────────────────────
  if find_vault_container; then
    local seal_status
    seal_status=$(vault_exec status -format=json 2>/dev/null || echo '{"sealed":true}')
    if echo "$seal_status" | grep -q '"sealed":false'; then
      ok "Vault: unsealed"
    else
      warn "Vault: SEALED"
      all_healthy=false
    fi
  else
    warn "Vault: container not found"
    all_healthy=false
  fi

  # ── Check Docker Swarm services ─────────────────────────────────────────
  local services=(
    "auth-api:2"
    "assistant-api:2"
    "rabbitmq:1"
    "postgres:1"
    "grafana:1"
    "traefik:1"
    "stalwart:1"
  )

  for svc_spec in "${services[@]}"; do
    local svc expected_replicas running
    IFS=':' read -r svc expected_replicas <<< "$svc_spec"
    running=$(docker service ps "personal-stack_${svc}" --filter "desired-state=running" \
      --format "{{.CurrentState}}" 2>/dev/null | grep -c "^Running" || true)
    if [ "$running" -ge "$expected_replicas" ]; then
      ok "${svc}: ${running}/${expected_replicas} running"
    else
      warn "${svc}: ${running}/${expected_replicas} running"
      all_healthy=false
    fi
  done

  # ── Check auth-api JWKS endpoint ────────────────────────────────────────
  local jwks_response
  local auth_container
  auth_container=$(docker ps --filter "name=personal-stack_auth-api" --format "{{.ID}}" | head -1)
  if [[ -n "$auth_container" ]]; then
    jwks_response=$(docker exec "$auth_container" \
      wget -qO- http://localhost:8081/api/oauth2/jwks 2>/dev/null || true)
    if [[ -n "$jwks_response" ]] && echo "$jwks_response" | grep -q '"keys"'; then
      local key_count
      key_count=$(echo "$jwks_response" | grep -o '"kid"' | wc -l)
      ok "JWKS endpoint: responding (${key_count} key(s))"
    else
      warn "JWKS endpoint: not responding or empty"
      all_healthy=false
    fi
  else
    warn "auth-api container not found for JWKS check"
    all_healthy=false
  fi

  # ── Check RabbitMQ connectivity ─────────────────────────────────────────
  local rmq_container
  rmq_container=$(docker ps --filter "name=personal-stack_rabbitmq" --format "{{.ID}}" | head -1)
  if [[ -n "$rmq_container" ]]; then
    if docker exec "$rmq_container" rabbitmq-diagnostics check_running > /dev/null 2>&1; then
      ok "RabbitMQ: running"
    else
      warn "RabbitMQ: health check failed"
      all_healthy=false
    fi
  else
    warn "RabbitMQ: container not found"
    all_healthy=false
  fi

  # ── Summary ──────────────────────────────────────────────────────────────
  if [[ "$all_healthy" == "true" ]]; then
    ok "All health checks passed"
    return 0
  else
    warn "Some health checks failed -- review above"
    return 1
  fi
}
