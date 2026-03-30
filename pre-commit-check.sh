#!/usr/bin/env bash
# Local CI gate mirroring .github/workflows/full.yml as closely as practical.
# Stages run sequentially; work within each stage runs in parallel.
set -euo pipefail

SERVICES_INFRA="postgres valkey rabbitmq"
SERVICES_FULL="postgres valkey rabbitmq vault auth-api assistant-api traefik auth-ui assistant-ui app-ui n8n grafana stalwart"
STACK_STARTED=0

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log()  { echo -e "${GREEN}[CHECK]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
fail() { echo -e "${RED}[FAIL]${NC} $*" >&2; }

TMPDIR_JOBS=$(mktemp -d)
trap 'rm -rf "$TMPDIR_JOBS"; teardown_stack' EXIT

dump_stack_logs() {
  local services=(
    postgres valkey rabbitmq vault auth-api assistant-api traefik
    auth-ui assistant-ui app-ui n8n grafana stalwart
  )

  warn "Dumping stack logs for diagnosis..."
  for svc in "${services[@]}"; do
    echo "========== $svc =========="
    docker compose logs "$svc" --tail 80 2>&1 || true
  done
}

teardown_stack() {
  if docker compose ps --quiet 2>/dev/null | grep -q .; then
    log "Tearing down stack..."
    docker compose down --remove-orphans --timeout 10 2>/dev/null || true
  fi
  STACK_STARTED=0
}

select_java_21() {
  local java21_home=""

  if [[ "$(uname -s)" == "Darwin" ]] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
    java21_home=$(/usr/libexec/java_home -v 21 2>/dev/null || true)
  elif [[ -n "${JAVA_HOME_21_X64:-}" ]]; then
    java21_home="${JAVA_HOME_21_X64}"
  fi

  if [[ -n "$java21_home" ]]; then
    export JAVA_HOME="$java21_home"
    export PATH="$JAVA_HOME/bin:$PATH"
    log "Using Java 21 at $JAVA_HOME"
  fi
}

run_pnpm_audit() {
  if ! pnpm audit --audit-level=high; then
    warn "pnpm audit reported high severity findings (non-blocking, mirroring CI)."
  fi
}

run_trivy_scan() {
  if command -v trivy >/dev/null 2>&1; then
    trivy fs --severity HIGH,CRITICAL .
    return
  fi

  docker run --rm \
    -v "$PWD:/workspace" \
    -w /workspace \
    aquasec/trivy:0.61.0 \
    fs --severity HIGH,CRITICAL .
}

run_gitleaks_scan() {
  if command -v gitleaks >/dev/null 2>&1; then
    gitleaks detect --source . --config .gitleaks.toml --no-banner
    return
  fi

  docker run --rm \
    -v "$PWD:/repo" \
    -w /repo \
    zricethezav/gitleaks:v8.25.1 \
    detect --source . --config .gitleaks.toml --no-banner
}

install_playwright_browser() {
  if [[ "$(uname -s)" == "Linux" ]]; then
    npx playwright install --with-deps chromium
  else
    npx playwright install chromium
  fi
}

verify_services_healthy() {
  local unhealthy=0

  log "  Verifying service health..."
  docker compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Health}}"

  for svc in $SERVICES_FULL; do
    local container="personal-stack-$svc"
    local health
    health=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$container" 2>/dev/null || echo "missing")

    if [[ "$health" == "unhealthy" || "$health" == "missing" ]]; then
      fail "Service $container is $health"
      unhealthy=1
    fi
  done

  if [ "$unhealthy" -ne 0 ]; then
    return 1
  fi
}

verify_database_migrations() {
  log "  Verifying database migrations..."
  docker exec personal-stack-postgres psql -U auth_user -d auth_db -c '\dt' | grep -q app_user
  docker exec personal-stack-postgres psql -U assistant_user -d assistant_db -c '\dt' | grep -q conversation
}

# Run a named job in background, capturing output. Usage: run_job <name> <cmd...>
run_job() {
  local name="$1"; shift
  local outfile="$TMPDIR_JOBS/${name}.out"
  local rc_file="$TMPDIR_JOBS/${name}.rc"
  (
    local rc=0
    if declare -F "$1" >/dev/null 2>&1; then
      if "$@" >"$outfile" 2>&1; then
        rc=0
      else
        rc=$?
      fi
    elif bash infra/scripts/run-strict-command.sh "$@" >"$outfile" 2>&1; then
      rc=0
    else
      rc=$?
    fi
    echo "$rc" >"$rc_file"
    exit "$rc"
  ) &
  echo $! >"$TMPDIR_JOBS/${name}.pid"
}

# Wait for all background jobs; print failures and exit 1 if any failed.
wait_jobs() {
  local stage="$1"; shift
  local names=("$@")
  local failed=0

  for name in "${names[@]}"; do
    local pid
    pid=$(cat "$TMPDIR_JOBS/${name}.pid")
    wait "$pid" || true

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
# Bootstrap shared dependencies once before parallel jobs.
# ─────────────────────────────────────────────────────────────────
select_java_21
log "Bootstrapping shared tooling"
pnpm install --frozen-lockfile

# ─────────────────────────────────────────────────────────────────
# STAGE 1 — Lint, formatting, and security scans
# ─────────────────────────────────────────────────────────────────
log "Stage 1: Lint, formatting, and security scans"

run_job "lint-auth-api"      ./gradlew :services:auth-api:detekt :services:auth-api:ktlintCheck
run_job "lint-assistant-api" ./gradlew :services:assistant-api:detekt :services:assistant-api:ktlintCheck
run_job "lint-kotlin-common" ./gradlew :libs:kotlin-common:detekt :libs:kotlin-common:ktlintCheck
run_job "lint-frontend"      bash -lc 'pnpm -r lint --max-warnings 0 && pnpm format:check'
run_job "scan-trivy"         run_trivy_scan
run_job "scan-pnpm-audit"    run_pnpm_audit
run_job "scan-gitleaks"      run_gitleaks_scan

wait_jobs "lint+security" \
  lint-auth-api lint-assistant-api lint-kotlin-common lint-frontend \
  scan-trivy scan-pnpm-audit scan-gitleaks

# ─────────────────────────────────────────────────────────────────
# STAGE 2 — Unit tests + architecture + Docker image builds (parallel)
# ─────────────────────────────────────────────────────────────────
log "Stage 2: Unit tests, architecture checks, and Docker image builds"

run_job "unit-auth-api"      ./gradlew :services:auth-api:test
run_job "unit-assistant-api" ./gradlew :services:assistant-api:test
run_job "unit-kotlin-common" ./gradlew :libs:kotlin-common:test
run_job "unit-frontend"      pnpm -r test -- --coverage
run_job "arch-frontend"      pnpm -r depcruise

# Build Docker images in parallel while tests run (needed for stage 4)
log "  Building Docker images in parallel..."
run_job "build-auth-api"      docker build -f services/auth-api/Dockerfile      -t personal-stack/auth-api:latest      .
run_job "build-assistant-api" docker build -f services/assistant-api/Dockerfile -t personal-stack/assistant-api:latest .
run_job "build-auth-ui"       docker build -f services/auth-ui/Dockerfile       -t personal-stack/auth-ui:latest       .
run_job "build-assistant-ui"  docker build --build-arg VITE_AUTH_URL=https://auth.jorisjonkers.test -f services/assistant-ui/Dockerfile -t personal-stack/assistant-ui:latest .
run_job "build-app-ui"        docker build --build-arg VITE_AUTH_URL=https://auth.jorisjonkers.test -f services/app-ui/Dockerfile -t personal-stack/app-ui:latest .

wait_jobs "unit+arch+build" \
  unit-auth-api unit-assistant-api unit-kotlin-common unit-frontend \
  arch-frontend \
  build-auth-api build-assistant-api build-auth-ui build-assistant-ui build-app-ui

log "Stage 2b: Kotlin architecture tests"
bash infra/scripts/run-strict-command.sh ./gradlew :services:auth-api:test :services:assistant-api:test --tests "*ArchitectureTest*"
log "Stage 'arch-kotlin' passed."

# ─────────────────────────────────────────────────────────────────
# STAGE 3 — Integration tests + coverage verification
# ─────────────────────────────────────────────────────────────────
log "Stage 3: Integration tests and coverage verification"

log "  Starting infrastructure services..."
docker compose up -d $SERVICES_INFRA --wait --wait-timeout 120

run_job "integ-auth-api"      ./gradlew :services:auth-api:integrationTest
run_job "integ-assistant-api" ./gradlew :services:assistant-api:integrationTest

wait_jobs "integration" integ-auth-api integ-assistant-api

run_job "coverage-auth-api"      ./gradlew :services:auth-api:jacocoTestCoverageVerification
run_job "coverage-assistant-api" ./gradlew :services:assistant-api:jacocoTestCoverageVerification
run_job "coverage-kotlin-common" ./gradlew :libs:kotlin-common:jacocoTestCoverageVerification

wait_jobs "coverage" coverage-auth-api coverage-assistant-api coverage-kotlin-common

log "  Stopping infrastructure services..."
docker compose down --remove-orphans --timeout 10 2>/dev/null || true

# ─────────────────────────────────────────────────────────────────
# STAGE 4 — System tests (full stack required)
# ─────────────────────────────────────────────────────────────────
log "Stage 4: System tests (full stack)"

log "  Installing Playwright browser..."
install_playwright_browser

log "  Starting full stack..."
# shellcheck disable=SC2086
docker compose up -d --no-build $SERVICES_FULL --wait --wait-timeout 300
STACK_STARTED=1

if ! verify_services_healthy; then
  dump_stack_logs
  exit 1
fi

log "  Waiting for Traefik routes to stabilize..."
check_route() {
  local route="$1" url="$2"
  for i in $(seq 1 30); do
    if curl -sfk --max-time 2 -o /dev/null "$url" 2>/dev/null; then return 0; fi
    if [ "$i" -eq 30 ]; then
      fail "Route $route ($url) not reachable after 30s"
      return 1
    fi
    sleep 1
  done
}
if ! check_route app-ui         "https://jorisjonkers.test/"; then dump_stack_logs; exit 1; fi
if ! check_route auth-ui        "https://auth.jorisjonkers.test/"; then dump_stack_logs; exit 1; fi
if ! check_route assistant-ui   "https://assistant.jorisjonkers.test/"; then dump_stack_logs; exit 1; fi
if ! check_route auth-api       "https://auth.jorisjonkers.test/api/actuator/health"; then dump_stack_logs; exit 1; fi
if ! check_route assistant-api  "https://assistant.jorisjonkers.test/api/actuator/health"; then dump_stack_logs; exit 1; fi

if ! verify_database_migrations; then
  dump_stack_logs
  exit 1
fi

log "  Running system tests..."
if ! bash infra/scripts/run-strict-command.sh ./gradlew :services:system-tests:test \
  -Dtest.auth-api.url=http://localhost:8081 \
  -Dtest.assistant-api.url=http://localhost:8082; then
  dump_stack_logs
  exit 1
fi

log "All checks passed."
