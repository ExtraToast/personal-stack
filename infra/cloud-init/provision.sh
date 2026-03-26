#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

DEPLOY_KEY="$HOME/.ssh/private-stack-deploy-key"
ROOT_KEY="$HOME/.ssh/private-stack-root-user"
TEMPLATE="cloud-init.template.yml"
RENDERED="cloud-init.rendered.yml"

# ── Helpers ──────────────────────────────────────────────────────

info()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
ok()    { printf '\033[1;32m OK\033[0m %s\n' "$*"; }
err()   { printf '\033[1;31mERR\033[0m %b\n' "$*" >&2; exit 1; }

generate_key_if_missing() {
  local path="$1" comment="$2"
  if [[ -f "$path" ]]; then
    ok "SSH key exists: $path"
  else
    info "Generating SSH key: $path"
    ssh-keygen -t ed25519 -C "$comment" -f "$path" -N "" -q
    ok "Created $path"
  fi
}

require_var() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    err "Required variable $name is not set in .env"
  fi
}

random_password() {
  openssl rand -base64 24 | tr -d '/+=' | head -c 32
}

# Auto-generate a password if the var is empty, and write it back to .env
ensure_password() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    local val
    val=$(random_password)
    eval "$name=\$val"
    if grep -q "^${name}=" .env; then
      sed -i "s|^${name}=.*|${name}=${val}|" .env
    else
      echo "${name}=${val}" >> .env
    fi
    info "Generated random value for $name"
  fi
}

# ── 1. SSH Keys ──────────────────────────────────────────────────

info "Checking SSH keys..."
generate_key_if_missing "$DEPLOY_KEY" "deploy@private-stack"
generate_key_if_missing "$ROOT_KEY" "root@private-stack"

# ── 2. Load .env ─────────────────────────────────────────────────

if [[ ! -f .env ]]; then
  cp .env.example .env
  info "Created .env from .env.example"
  info "Fill in the Contabo credentials and VPS identifiers, then re-run."
  exit 1
fi

# shellcheck source=/dev/null
source .env

# ── 3. Validate Contabo credentials ─────────────────────────────

require_var CONTABO_OAUTH2_CLIENT_ID
require_var CONTABO_OAUTH2_CLIENT_SECRET
require_var CONTABO_OAUTH2_USER
require_var CONTABO_OAUTH2_PASSWORD
require_var CONTABO_INSTANCE_ID
require_var CONTABO_IMAGE_ID
require_var GITHUB_REPO

CNTB_AUTH=(
  --oauth2-clientid "$CONTABO_OAUTH2_CLIENT_ID"
  --oauth2-client-secret "$CONTABO_OAUTH2_CLIENT_SECRET"
  --oauth2-user "$CONTABO_OAUTH2_USER"
  --oauth2-password "$CONTABO_OAUTH2_PASSWORD"
)

# ── 4. Auto-generate Docker Swarm secret values ─────────────────

info "Ensuring Docker Swarm secret values..."
ensure_password POSTGRES_PASSWORD
ensure_password RABBITMQ_PASSWORD
ensure_password GRAFANA_ADMIN_PASSWORD
ensure_password N8N_DB_PASSWORD

# Defaults for usernames (not passwords, but must be non-empty)
: "${POSTGRES_USER:=postgres}"
: "${RABBITMQ_USER:=guest}"
: "${GRAFANA_ADMIN_USER:=admin}"
: "${N8N_DB_USER:=n8n_user}"
: "${CF_DNS_API_TOKEN:=placeholder}"

# ── 5. Register deploy key on GitHub ─────────────────────────────

info "Checking GitHub deploy key..."
if gh repo deploy-key list 2>/dev/null | grep -q "private-stack-deploy-key"; then
  ok "Deploy key already registered on GitHub"
else
  info "Adding deploy key to GitHub..."
  gh repo deploy-key add "${DEPLOY_KEY}.pub" \
    --title "private-stack-deploy-key" --repo ExtraToast/private-stack
  ok "Deploy key registered"
fi

# ── 6. Upload SSH key to Contabo ─────────────────────────────────

if [[ -n "${CONTABO_SSH_SECRET_ID:-}" ]]; then
  ok "Contabo SSH secret ID already set: $CONTABO_SSH_SECRET_ID"
else
  info "Checking for existing SSH secret on Contabo..."
  EXISTING_SECRETS=""
  EXISTING_SECRETS=$(cntb get secrets \
    --name "private-stack-root-user" \
    --type ssh \
    "${CNTB_AUTH[@]}" \
    -o json 2>&1 || true)

  CONTABO_SSH_SECRET_ID=$(echo "$EXISTING_SECRETS" | grep -oP '"secretId"\s*:\s*\K\d+' | head -1 || true)

  if [[ -n "$CONTABO_SSH_SECRET_ID" ]]; then
    ok "Found existing SSH secret: ID=$CONTABO_SSH_SECRET_ID"
  else
    info "Creating new SSH secret on Contabo..."
    CREATE_OUTPUT=""
    if ! CREATE_OUTPUT=$(cntb create secret \
      --name "private-stack-root-user" \
      --type ssh \
      --value "$(cat "${ROOT_KEY}.pub")" \
      "${CNTB_AUTH[@]}" \
      -o json 2>&1); then
      err "cntb create secret failed:\n$CREATE_OUTPUT"
    fi

    CONTABO_SSH_SECRET_ID=$(echo "$CREATE_OUTPUT" | grep -oP '"secretId"\s*:\s*\K\d+' | head -1 || true)

    if [[ -z "$CONTABO_SSH_SECRET_ID" ]]; then
      err "Could not extract secret ID from response:\n$CREATE_OUTPUT"
    fi

    ok "Contabo SSH secret created: ID=$CONTABO_SSH_SECRET_ID"
  fi

  if grep -q "^CONTABO_SSH_SECRET_ID=" .env; then
    sed -i "s/^CONTABO_SSH_SECRET_ID=.*/CONTABO_SSH_SECRET_ID=$CONTABO_SSH_SECRET_ID/" .env
  else
    echo "CONTABO_SSH_SECRET_ID=$CONTABO_SSH_SECRET_ID" >> .env
  fi

  ok "Saved CONTABO_SSH_SECRET_ID=$CONTABO_SSH_SECRET_ID to .env"
fi

# ── 7. Render cloud-init template ───────────────────────────────

info "Rendering cloud-init template..."

SSH_PUB_KEY=$(cat "${ROOT_KEY}.pub")
DEPLOY_KEY_B64=$(base64 -w 0 < "$DEPLOY_KEY")

sed \
  -e "s|__SSH_PUBLIC_KEY__|${SSH_PUB_KEY}|g" \
  -e "s|__DEPLOY_KEY_PRIVATE_B64__|${DEPLOY_KEY_B64}|g" \
  -e "s|__GITHUB_REPO__|${GITHUB_REPO}|g" \
  -e "s|__POSTGRES_USER__|${POSTGRES_USER}|g" \
  -e "s|__POSTGRES_PASSWORD__|${POSTGRES_PASSWORD}|g" \
  -e "s|__RABBITMQ_USER__|${RABBITMQ_USER}|g" \
  -e "s|__RABBITMQ_PASSWORD__|${RABBITMQ_PASSWORD}|g" \
  -e "s|__CF_DNS_API_TOKEN__|${CF_DNS_API_TOKEN}|g" \
  -e "s|__GRAFANA_ADMIN_USER__|${GRAFANA_ADMIN_USER}|g" \
  -e "s|__GRAFANA_ADMIN_PASSWORD__|${GRAFANA_ADMIN_PASSWORD}|g" \
  -e "s|__N8N_DB_USER__|${N8N_DB_USER}|g" \
  -e "s|__N8N_DB_PASSWORD__|${N8N_DB_PASSWORD}|g" \
  "$TEMPLATE" > "$RENDERED"

ok "Rendered $RENDERED"

# ── 8. Confirmation ──────────────────────────────────────────────

echo ""
echo "┌─────────────────────────────────────────────────────────┐"
echo "│  REINSTALL VPS                                          │"
echo "├─────────────────────────────────────────────────────────┤"
printf "│  Instance ID : %-40s│\n" "$CONTABO_INSTANCE_ID"
printf "│  Image ID    : %-40s│\n" "$CONTABO_IMAGE_ID"
printf "│  SSH Secret  : %-40s│\n" "$CONTABO_SSH_SECRET_ID"
echo "├─────────────────────────────────────────────────────────┤"
echo "│  WARNING: This will WIPE the VPS and reinstall it.      │"
echo "└─────────────────────────────────────────────────────────┘"
echo ""
read -rp "Are you sure? [y/N] " confirm
if [[ ! "$confirm" =~ ^[yY]$ ]]; then
  info "Aborted."
  exit 0
fi

# ── 9. Reinstall ─────────────────────────────────────────────────

info "Reinstalling VPS..."
cntb reinstall instance "$CONTABO_INSTANCE_ID" \
  --imageId "$CONTABO_IMAGE_ID" \
  --sshKeys "$CONTABO_SSH_SECRET_ID" \
  --userData "$(cat "$RENDERED")" \
  --defaultUser root \
  "${CNTB_AUTH[@]}"

# ── 10. Done ─────────────────────────────────────────────────────

echo ""
ok "VPS reinstall initiated."
echo ""
info "Next steps:"
echo "  1. Wait a few minutes for cloud-init to finish (installs Docker, clones repo,"
echo "     deploys stack, initializes Vault, and redeploys with real secrets)"
echo "  2. SSH in:  ssh -i ~/.ssh/private-stack-root-user -p 2222 deploy@<VPS_IP>"
echo "  3. Check:   docker stack ps private-stack"
