#!/usr/bin/env bash
# Pre-commit check: builds fresh images, runs all checks in parallel, then system tests.
# Stages run sequentially; work within each stage runs in parallel.
# Fails as fast as possible.
set -euo pipefail

SERVICES_FULL="postgres valkey rabbitmq vault auth-api assistant-api traefik auth-ui assistant-ui app-ui n8n grafana stalwart"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log()  { echo -e "${GREEN}[CHECK]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
fail() { echo -e "${RED}[FAIL]${NC} $*" >&2; }

TMPDIR_JOBS=$(mktemp -d)
trap 'rm -rf "$TMPDIR_JOBS"; teardown_stack' EXIT

teardown_stack() {
  if docker compose ps --quiet 2>/dev/null | grep -q .; then
    log "Tearing down stack..."
    docker compose down --remove-orphans --timeout 10 2>/dev/null || true
  fi
}

# Run a named job in background, capturing output. Usage: run_job <name> <cmd...>
run_job() {
  local name="$1"; shift
  local outfile="$TMPDIR_JOBS/${name}.out"
  local rc_file="$TMPDIR_JOBS/${name}.rc"
  (
    "$@" >"$outfile" 2>&1
    echo $? >"$rc_file"
  ) &
  echo $! >"$TMPDIR_JOBS/${name}.pid"
}

# Wait for all background jobs; print failures and exit 1 if any failed.
wait_jobs() {
  local stage="$1"; shift
  local names=("$@")
  local failed=0
  wait
  for name in "${names[@]}"; do
    local rc_file="$TMPDIR_JOBS/${name}.rc"
    local rc=0
    [ -f "$rc_file" ] && rc=$(cat "$rc_file")
    if [ "$rc" -ne 0 ]; then
      fail "[$stage] $name FAILED (exit $rc):"
      cat "$TMPDIR_JOBS/${name}.out" >&2
      failed=1
    fi
  done
  if [ "$failed" -ne 0 ]; then
    fail "Stage '$stage' failed — aborting."
    exit 1
  fi
  log "Stage '$stage' passed."
}

# ─────────────────────────────────────────────────────────────────
# STAGE 1 — Lint & static analysis (fastest, runs first)
# ─────────────────────────────────────────────────────────────────
log "Stage 1: Lint & static analysis"

run_job "lint-auth-api"      ./gradlew :services:auth-api:detekt :services:auth-api:ktlintCheck
run_job "lint-assistant-api" ./gradlew :services:assistant-api:detekt :services:assistant-api:ktlintCheck
run_job "lint-kotlin-common" ./gradlew :libs:kotlin-common:detekt :libs:kotlin-common:ktlintCheck
run_job "lint-frontend"      bash -c 'pnpm install --frozen-lockfile && pnpm -r lint --max-warnings 0 && pnpm format:check'

wait_jobs "lint" lint-auth-api lint-assistant-api lint-kotlin-common lint-frontend

# ─────────────────────────────────────────────────────────────────
# STAGE 2 — Unit tests + architecture + Docker image builds (parallel)
# ─────────────────────────────────────────────────────────────────
log "Stage 2: Unit tests, architecture checks, and Docker image builds"

run_job "unit-auth-api"      ./gradlew :services:auth-api:test
run_job "unit-assistant-api" ./gradlew :services:assistant-api:test
run_job "unit-kotlin-common" ./gradlew :libs:kotlin-common:test
run_job "unit-frontend"      bash -c 'pnpm install --frozen-lockfile && pnpm -r test'
run_job "arch-kotlin"        ./gradlew :services:auth-api:test :services:assistant-api:test --tests "*ArchitectureTest*"
run_job "arch-frontend"      bash -c 'pnpm install --frozen-lockfile && pnpm -r depcruise'

# Build Docker images in parallel while tests run (needed for stage 4)
log "  Building Docker images in parallel..."
run_job "build-auth-api"      docker build -f services/auth-api/Dockerfile      -t personal-stack/auth-api:latest      .
run_job "build-assistant-api" docker build -f services/assistant-api/Dockerfile -t personal-stack/assistant-api:latest .
run_job "build-auth-ui"       docker build -f services/auth-ui/Dockerfile       -t personal-stack/auth-ui:latest       .
run_job "build-assistant-ui"  docker build -f services/assistant-ui/Dockerfile  -t personal-stack/assistant-ui:latest  .
run_job "build-app-ui"        docker build -f services/app-ui/Dockerfile        -t personal-stack/app-ui:latest        .

wait_jobs "unit+arch+build" \
  unit-auth-api unit-assistant-api unit-kotlin-common unit-frontend \
  arch-kotlin arch-frontend \
  build-auth-api build-assistant-api build-auth-ui build-assistant-ui build-app-ui

# ─────────────────────────────────────────────────────────────────
# STAGE 3 — Integration tests (requires database + messaging services)
# ─────────────────────────────────────────────────────────────────
log "Stage 3: Integration tests"

log "  Starting infrastructure services..."
docker compose up -d postgres valkey rabbitmq --wait --wait-timeout 120

run_job "integ-auth-api"      ./gradlew :services:auth-api:integrationTest
run_job "integ-assistant-api" ./gradlew :services:assistant-api:integrationTest

wait_jobs "integration" integ-auth-api integ-assistant-api

log "  Stopping infrastructure services..."
docker compose down --remove-orphans --timeout 10 2>/dev/null || true

# ─────────────────────────────────────────────────────────────────
# STAGE 4 — System tests (full stack required)
# ─────────────────────────────────────────────────────────────────
log "Stage 4: System tests (full stack)"

log "  Starting full stack..."
# shellcheck disable=SC2086
docker compose up -d --no-build $SERVICES_FULL --wait --wait-timeout 300

log "  Waiting for Traefik routes to stabilize..."
check_route() {
  local route="$1" url="$2"
  for i in $(seq 1 30); do
    if curl -sf --max-time 2 -o /dev/null "$url" 2>/dev/null; then return 0; fi
    if [ "$i" -eq 30 ]; then warn "Route $route ($url) not reachable after 30s"; fi
    sleep 1
  done
}
check_route app-ui         "http://jorisjonkers.test:80/"
check_route auth-ui        "http://auth.jorisjonkers.test:80/"
check_route assistant-ui   "http://assistant.jorisjonkers.test:80/"
check_route auth-api       "http://auth.jorisjonkers.test:80/api/actuator/health"
check_route assistant-api  "http://assistant.jorisjonkers.test:80/api/actuator/health"

log "  Running system tests..."
./gradlew :services:system-tests:test \
  -Dtest.auth-api.url=http://localhost:8081 \
  -Dtest.assistant-api.url=http://localhost:8082

log "All checks passed."
