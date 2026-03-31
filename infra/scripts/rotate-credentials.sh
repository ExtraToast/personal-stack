#!/usr/bin/env bash
# rotate-credentials.sh — Scheduled credential rotation for personal-stack.
#
# Rotates secrets on a tiered schedule:
#   Tier 1 (weekly):    AppRole secret_ids
#   Tier 2 (monthly):   RabbitMQ, OAuth2 client secrets, admin passwords
#   Tier 3 (quarterly): JWT signing key, GitHub secrets
#
# Usage:
#   rotate-credentials.sh [OPTIONS]
#
# Options:
#   --all              Rotate all credentials for the current schedule
#   --tier 1|2|3       Rotate only a specific tier
#   --only TYPE        Rotate one type: approle|rabbitmq|oauth2|jwt|admin|github
#   --dry-run          Show what would be rotated without changes
#   --force            Skip confirmation prompts
#   --notify WEBHOOK   POST results to webhook (n8n)
#   --log FILE         Log file (default: /opt/personal-stack/rotate.log)
set -euo pipefail

STACK_DIR="${STACK_DIR:-/opt/personal-stack}"
LOG_FILE="${STACK_DIR}/rotate.log"

# Parse the script's own location
ROTATE_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="${ROTATE_SCRIPT_DIR}/lib"

# ── Source libraries ───────────────────────────────────────────────────────
source "${LIB_DIR}/common.sh"
source "${LIB_DIR}/swarm-secrets.sh"
source "${LIB_DIR}/generate-compose-overlay.sh"
source "${LIB_DIR}/rotate-approle.sh"
source "${LIB_DIR}/rotate-rabbitmq.sh"
source "${LIB_DIR}/rotate-oauth2.sh"
source "${LIB_DIR}/rotate-jwt.sh"
source "${LIB_DIR}/rotate-admin.sh"
source "${LIB_DIR}/rotate-github.sh"
source "${LIB_DIR}/health.sh"

# ── Defaults ──────────────────────────────────────────────────────────────
MODE="all"        # all | tier | only
TIER=""
ONLY=""
DRY_RUN="false"
FORCE="false"
NOTIFY_WEBHOOK=""
ROTATION_RESULTS=()

# ── Argument parsing ──────────────────────────────────────────────────────
usage() {
  cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Rotate credentials for personal-stack on a tiered schedule.

Options:
  --all              Rotate all tiers due for the current schedule (default)
  --tier 1|2|3       Rotate only credentials in the specified tier
  --only TYPE        Rotate a single type:
                       approle, rabbitmq, oauth2, jwt, admin, github
  --dry-run          Show what would be rotated without making changes
  --force            Skip confirmation prompts
  --notify WEBHOOK   POST results to a webhook URL (e.g., n8n)
  --log FILE         Log file path (default: ${LOG_FILE})
  -h, --help         Show this help

Tier schedule:
  Tier 1 (weekly):    AppRole secret_ids
  Tier 2 (monthly):   RabbitMQ, OAuth2 client secrets, admin passwords
  Tier 3 (quarterly): JWT signing key, GitHub secrets
EOF
  exit 0
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --all)       MODE="all"; shift ;;
    --tier)      MODE="tier"; TIER="$2"; shift 2 ;;
    --only)      MODE="only"; ONLY="$2"; shift 2 ;;
    --dry-run)   DRY_RUN="true"; shift ;;
    --force)     FORCE="true"; shift ;;
    --notify)    NOTIFY_WEBHOOK="$2"; shift 2 ;;
    --log)       LOG_FILE="$2"; shift 2 ;;
    -h|--help)   usage ;;
    *)           die "Unknown option: $1" ;;
  esac
done

export DRY_RUN

# ── Determine what to rotate ─────────────────────────────────────────────
should_rotate_tier1() {
  [[ "$MODE" == "all" ]] || [[ "$MODE" == "tier" && "$TIER" == "1" ]]
}

should_rotate_tier2() {
  if [[ "$MODE" == "all" ]]; then
    # Check if it's the first week of the month (approximate monthly)
    local day_of_month
    day_of_month=$(date +%d)
    [[ "$day_of_month" -le 7 ]]
  elif [[ "$MODE" == "tier" && "$TIER" == "2" ]]; then
    return 0
  else
    return 1
  fi
}

should_rotate_tier3() {
  if [[ "$MODE" == "all" ]]; then
    # Check if it's the first week of Jan/Apr/Jul/Oct (approximate quarterly)
    local month day_of_month
    month=$(date +%m)
    day_of_month=$(date +%d)
    [[ "$day_of_month" -le 7 ]] && [[ "$month" == "01" || "$month" == "04" || "$month" == "07" || "$month" == "10" ]]
  elif [[ "$MODE" == "tier" && "$TIER" == "3" ]]; then
    return 0
  else
    return 1
  fi
}

should_rotate() {
  local type="$1"
  if [[ "$MODE" == "only" ]]; then
    [[ "$ONLY" == "$type" ]]
  else
    case "$type" in
      approle)  should_rotate_tier1 ;;
      rabbitmq|oauth2|admin) should_rotate_tier2 ;;
      jwt|github) should_rotate_tier3 ;;
      *) return 1 ;;
    esac
  fi
}

# ── Confirmation ──────────────────────────────────────────────────────────
confirm_rotation() {
  if [[ "$FORCE" == "true" ]] || [[ "$DRY_RUN" == "true" ]]; then
    return 0
  fi

  printf "\n${BOLD}The following credentials will be rotated:${RESET}\n"
  should_rotate approle  && printf "  - AppRole secret_ids (Tier 1)\n"
  should_rotate rabbitmq && printf "  - RabbitMQ password (Tier 2)\n"
  should_rotate oauth2   && printf "  - OAuth2 client secrets (Tier 2)\n"
  should_rotate admin    && printf "  - Admin passwords (Tier 2)\n"
  should_rotate jwt      && printf "  - JWT signing key (Tier 3)\n"
  should_rotate github   && printf "  - GitHub secrets (Tier 3)\n"
  printf "\n"

  read -rp "  Continue? [y/N] " answer
  [[ "$answer" =~ ^[Yy]$ ]] || { info "Aborted."; exit 0; }
}

# ── Count total steps ─────────────────────────────────────────────────────
count_steps() {
  local count=2  # pre-flight + post-flight always run
  should_rotate approle  && count=$((count + 1))
  should_rotate rabbitmq && count=$((count + 1))
  should_rotate oauth2   && count=$((count + 1))
  should_rotate admin    && count=$((count + 1))
  should_rotate jwt      && count=$((count + 1))
  should_rotate github   && count=$((count + 1))
  count=$((count + 2))  # deploy + health check
  echo "$count"
}

# ══════════════════════════════════════════════════════════════════════════
# ── MAIN ──────────────────────────────────────────────────────────────────
# ══════════════════════════════════════════════════════════════════════════

TOTAL_STEPS=$(count_steps)
STARTED_AT=$(date +%s)

trap 'echo "[$(date "+%Y-%m-%d %H:%M:%S")] ERR: rotate-credentials.sh failed at line ${LINENO}" >> "$LOG_FILE"' ERR

# ── Banner ──────────────────────────────────────────────────────────────
printf "\n${BOLD}"
printf "  +-----------------------------------------+\n"
printf "  |     personal-stack credential rotation   |\n"
printf "  +-----------------------------------------+${RESET}\n"
[[ "$DRY_RUN" == "true" ]] && printf "  ${YELLOW}DRY RUN — no changes will be made${RESET}\n"
printf "\n"

echo "[$(date '+%Y-%m-%d %H:%M:%S')] rotate-credentials.sh started (mode=${MODE})" >> "$LOG_FILE"

# ── Pre-flight ────────────────────────────────────────────────────────────
step "Pre-flight checks"

load_vault_keys
ok "Vault keys loaded"
load_app_secrets

find_vault_container || die "Vault container not running. Is the stack up?"
ok "Vault container: ${VAULT_CONTAINER:0:12}"

ensure_vault_unsealed

init_secret_versions
ok "Secret version file initialized"

confirm_rotation

# ── Tier 1: AppRole ──────────────────────────────────────────────────────
if should_rotate approle; then
  step "Rotating AppRole secret_ids (Tier 1 — weekly)"
  rotate_approle_credentials
fi

# ── Tier 2: RabbitMQ ─────────────────────────────────────────────────────
if should_rotate rabbitmq; then
  step "Rotating RabbitMQ password (Tier 2 — monthly)"
  rotate_rabbitmq_credentials
fi

# ── Tier 2: OAuth2 client secrets ────────────────────────────────────────
if should_rotate oauth2; then
  step "Rotating OAuth2 client secrets (Tier 2 — monthly)"
  rotate_oauth2_client_secrets
fi

# ── Tier 2: Admin passwords ─────────────────────────────────────────────
if should_rotate admin; then
  step "Rotating admin passwords (Tier 2 — monthly)"
  rotate_admin_passwords
fi

# ── Tier 3: JWT signing key ─────────────────────────────────────────────
if should_rotate jwt; then
  step "Rotating JWT signing key (Tier 3 — quarterly)"
  rotate_jwt_signing_key
fi

# ── Tier 3: GitHub secrets ──────────────────────────────────────────────
if should_rotate github; then
  step "Rotating GitHub secrets (Tier 3 — quarterly)"
  rotate_github_secrets
fi

# ── Deploy with updated secrets ──────────────────────────────────────────
step "Deploying with updated secrets"

if [[ "$DRY_RUN" == "true" ]]; then
  ok "[DRY RUN] Would generate compose overlay and deploy stack"
else
  # Generate the compose overlay with versioned secret names
  generate_compose_overlay "${STACK_DIR}/docker-compose.secrets.yml"
  ok "Compose overlay generated"

  # Deploy (rolling update — no stack teardown)
  deploy_stack
  ok "Stack deployed with updated secrets"

  # Wait for Vault and unseal if needed
  if wait_for_vault_api 90; then
    find_vault_container
    ensure_vault_unsealed
  else
    warn "Vault did not come back — may need manual unseal"
  fi
fi

# ── Post-flight health checks ───────────────────────────────────────────
step "Post-rotation health checks"

if [[ "$DRY_RUN" == "true" ]]; then
  ok "[DRY RUN] Would run health checks"
else
  # Give services time to restart with new secrets
  info "Waiting 30s for rolling updates to propagate..."
  sleep 30

  if verify_rotation; then
    HEALTH_STATUS="healthy"
  else
    HEALTH_STATUS="degraded"
  fi

  # Re-apply Vault OIDC config if OAuth2 secrets were rotated
  if should_rotate oauth2; then
    info "Re-applying Vault OIDC configuration..."
    reapply_vault_oidc_config || warn "OIDC config re-apply failed (may need manual fix)"
  fi
fi

# ── Summary & notification ───────────────────────────────────────────────
ENDED_AT=$(date +%s)
DURATION=$((ENDED_AT - STARTED_AT))

printf "\n${BOLD}  Rotation Summary${RESET}\n"
printf "  %-25s  %s\n" "${DIM}CREDENTIAL${RESET}" "${DIM}STATUS${RESET}"
printf "  %-25s  %s\n" "-------------------------" "--------"
for result in "${ROTATION_RESULTS[@]}"; do
  IFS=':' read -r cred status <<< "$result"
  if [[ "$status" == "success" ]]; then
    printf "  %-25s  ${GREEN}%s${RESET}\n" "$cred" "$status"
  elif [[ "$status" == "skipped" ]]; then
    printf "  %-25s  ${YELLOW}%s${RESET}\n" "$cred" "$status"
  else
    printf "  %-25s  ${RED}%s${RESET}\n" "$cred" "$status"
  fi
done
printf "  ${DIM}Duration: ${DURATION}s${RESET}\n"

if [[ "$DRY_RUN" != "true" ]]; then
  # Clean up old secret versions
  info "Cleaning up old secret versions..."
  for secret_name in "${VERSIONED_SECRETS[@]}"; do
    cleanup_old_secret "$secret_name"
  done

  # Send notification
  local summary_text
  summary_text="Rotated: $(IFS=,; echo "${ROTATION_RESULTS[*]:-none}"). Duration: ${DURATION}s. Health: ${HEALTH_STATUS:-unknown}"
  notify_result "${HEALTH_STATUS:-unknown}" "$summary_text"
fi

printf "\n${BOLD}${GREEN}  +-----------------------------------------+${RESET}\n"
printf "${BOLD}${GREEN}  |          Rotation complete               |${RESET}\n"
printf "${BOLD}${GREEN}  +-----------------------------------------+${RESET}\n"
printf "  ${DIM}Full log: ${LOG_FILE}${RESET}\n\n"

echo "[$(date '+%Y-%m-%d %H:%M:%S')] rotate-credentials.sh complete (${DURATION}s)" >> "$LOG_FILE"
