#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

DEPLOY_KEY="$HOME/.ssh/personal-stack-deploy-key"
ROOT_KEY="$HOME/.ssh/personal-stack-root-user"
TEMPLATE="cloud-init.template.yml"
RENDERED="cloud-init.rendered.yml"

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

upsert_env_value() {
  local key="$1" value="$2"
  python3 - "$key" "$value" .env <<'PY'
import pathlib
import sys

key = sys.argv[1]
value = sys.argv[2]
path = pathlib.Path(sys.argv[3])
lines = path.read_text().splitlines()
needle = f"{key}="

for index, line in enumerate(lines):
    if line.startswith(needle):
        lines[index] = f"{key}={value}"
        break
else:
    lines.append(f"{key}={value}")

path.write_text("\n".join(lines) + "\n")
PY
}

random_password() {
  openssl rand -base64 24 | tr -d '/+=' | head -c 32
}

ensure_password() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    local val
    val="$(random_password)"
    printf -v "$name" '%s' "$val"
    upsert_env_value "$name" "$val"
    info "Generated random value for $name"
  fi
}

extract_secret_id() {
  python3 - "$1" <<'PY'
import re
import sys

text = sys.argv[1]
match = re.search(r'"secretId"\s*:\s*(\d+)', text)
if match:
    print(match.group(1))
PY
}

render_template() {
  local output_file="$1"

  python3 - "$TEMPLATE" "$output_file" <<'PY'
import os
import pathlib
import sys

template_path = pathlib.Path(sys.argv[1])
output_path = pathlib.Path(sys.argv[2])
template = template_path.read_text()

def sq(value: str) -> str:
    return value.replace("'", "'\"'\"'")

placeholders = {
    "__SSH_PUBLIC_KEY__": sq(os.environ["SSH_PUB_KEY"]),
    "__DEPLOY_KEY_PRIVATE_B64__": sq(os.environ["DEPLOY_KEY_B64"]),
    "__GITHUB_REPO__": sq(os.environ["GITHUB_REPO"]),
    "__IMAGE_TAG__": sq(os.environ["IMAGE_TAG"]),
    "__POSTGRES_USER__": sq(os.environ["POSTGRES_USER"]),
    "__POSTGRES_PASSWORD__": sq(os.environ["POSTGRES_PASSWORD"]),
    "__RABBITMQ_USER__": sq(os.environ["RABBITMQ_USER"]),
    "__RABBITMQ_PASSWORD__": sq(os.environ["RABBITMQ_PASSWORD"]),
    "__CF_DNS_API_TOKEN__": sq(os.environ["CF_DNS_API_TOKEN"]),
    "__GRAFANA_ADMIN_USER__": sq(os.environ["GRAFANA_ADMIN_USER"]),
    "__GRAFANA_ADMIN_PASSWORD__": sq(os.environ["GRAFANA_ADMIN_PASSWORD"]),
    "__N8N_DB_USER__": sq(os.environ["N8N_DB_USER"]),
    "__N8N_DB_PASSWORD__": sq(os.environ["N8N_DB_PASSWORD"]),
    "__AUTH_DB_USER__": sq(os.environ["AUTH_DB_USER"]),
    "__AUTH_DB_PASSWORD__": sq(os.environ["AUTH_DB_PASSWORD"]),
    "__ASSISTANT_DB_USER__": sq(os.environ["ASSISTANT_DB_USER"]),
    "__ASSISTANT_DB_PASSWORD__": sq(os.environ["ASSISTANT_DB_PASSWORD"]),
    "__STALWART_ADMIN_USER__": sq(os.environ["STALWART_ADMIN_USER"]),
    "__STALWART_ADMIN_PASSWORD__": sq(os.environ["STALWART_ADMIN_PASSWORD"]),
    "__N8N_OAUTH_CLIENT_SECRET__": sq(os.environ.get("N8N_OAUTH_CLIENT_SECRET", "")),
    "__GRAFANA_OAUTH_CLIENT_SECRET__": sq(os.environ.get("GRAFANA_OAUTH_CLIENT_SECRET", "")),
    "__VAULT_OIDC_CLIENT_SECRET__": sq(os.environ.get("VAULT_OIDC_CLIENT_SECRET", "")),
    "__GHCR_USER__": sq(os.environ["GHCR_USER"]),
    "__GHCR_TOKEN__": sq(os.environ["GHCR_TOKEN"]),
}

for placeholder, value in placeholders.items():
    template = template.replace(placeholder, value)

output_path.write_text(template)
PY
}

info "Checking SSH keys..."
generate_key_if_missing "$DEPLOY_KEY" "deploy@personal-stack"
generate_key_if_missing "$ROOT_KEY" "root@personal-stack"

if [[ ! -f .env ]]; then
  cp .env.example .env
  info "Created .env from .env.example"
  info "Fill in the Contabo credentials and VPS identifiers, then re-run."
  exit 1
fi

# shellcheck source=/dev/null
source .env

require_var CONTABO_OAUTH2_CLIENT_ID
require_var CONTABO_OAUTH2_CLIENT_SECRET
require_var CONTABO_OAUTH2_USER
require_var CONTABO_OAUTH2_PASSWORD
require_var CONTABO_INSTANCE_ID
require_var CONTABO_IMAGE_ID
require_var GITHUB_REPO
require_var GHCR_USER
require_var GHCR_TOKEN

CNTB_AUTH=(
  --oauth2-clientid "$CONTABO_OAUTH2_CLIENT_ID"
  --oauth2-client-secret "$CONTABO_OAUTH2_CLIENT_SECRET"
  --oauth2-user "$CONTABO_OAUTH2_USER"
  --oauth2-password "$CONTABO_OAUTH2_PASSWORD"
)

info "Ensuring bootstrap secrets..."
ensure_password POSTGRES_PASSWORD
ensure_password RABBITMQ_PASSWORD
ensure_password GRAFANA_ADMIN_PASSWORD
ensure_password N8N_DB_PASSWORD
ensure_password AUTH_DB_PASSWORD
ensure_password ASSISTANT_DB_PASSWORD
ensure_password STALWART_ADMIN_PASSWORD

: "${POSTGRES_USER:=postgres}"
: "${RABBITMQ_USER:=appuser}"
: "${GRAFANA_ADMIN_USER:=admin}"
: "${N8N_DB_USER:=n8n_user}"
: "${AUTH_DB_USER:=auth_user}"
: "${ASSISTANT_DB_USER:=assistant_user}"
: "${STALWART_ADMIN_USER:=admin}"
: "${IMAGE_TAG:=latest}"
: "${N8N_OAUTH_CLIENT_SECRET:=}"
: "${GRAFANA_OAUTH_CLIENT_SECRET:=}"
: "${VAULT_OIDC_CLIENT_SECRET:=}"
require_var CF_DNS_API_TOKEN

info "Checking GitHub deploy key..."
if gh repo deploy-key list 2>/dev/null | grep -q "personal-stack-deploy-key"; then
  ok "Deploy key already registered on GitHub"
else
  info "Adding deploy key to GitHub..."
  gh repo deploy-key add "${DEPLOY_KEY}.pub" \
    --title "personal-stack-deploy-key" --repo ExtraToast/personal-stack
  ok "Deploy key registered"
fi

if [[ -n "${CONTABO_SSH_SECRET_ID:-}" ]]; then
  ok "Contabo SSH secret ID already set: $CONTABO_SSH_SECRET_ID"
else
  info "Checking for existing SSH secret on Contabo..."
  EXISTING_SECRETS="$(cntb get secrets \
    --name "personal-stack-root-user" \
    --type ssh \
    "${CNTB_AUTH[@]}" \
    -o json 2>&1 || true)"

  CONTABO_SSH_SECRET_ID="$(extract_secret_id "$EXISTING_SECRETS" || true)"

  if [[ -n "$CONTABO_SSH_SECRET_ID" ]]; then
    ok "Found existing SSH secret: ID=$CONTABO_SSH_SECRET_ID"
  else
    info "Creating new SSH secret on Contabo..."
    if ! CREATE_OUTPUT="$(cntb create secret \
      --name "personal-stack-root-user" \
      --type ssh \
      --value "$(cat "${ROOT_KEY}.pub")" \
      "${CNTB_AUTH[@]}" \
      -o json 2>&1)"; then
      err "cntb create secret failed:\n$CREATE_OUTPUT"
    fi

    CONTABO_SSH_SECRET_ID="$(extract_secret_id "$CREATE_OUTPUT" || true)"

    if [[ -z "$CONTABO_SSH_SECRET_ID" ]]; then
      err "Could not extract secret ID from response:\n$CREATE_OUTPUT"
    fi

    ok "Contabo SSH secret created: ID=$CONTABO_SSH_SECRET_ID"
  fi

  upsert_env_value CONTABO_SSH_SECRET_ID "$CONTABO_SSH_SECRET_ID"
  ok "Saved CONTABO_SSH_SECRET_ID=$CONTABO_SSH_SECRET_ID to .env"
fi

info "Rendering cloud-init template..."

export SSH_PUB_KEY
SSH_PUB_KEY="$(cat "${ROOT_KEY}.pub")"
export DEPLOY_KEY_B64
DEPLOY_KEY_B64="$(base64 < "${DEPLOY_KEY}" | tr -d '\n')"

export GITHUB_REPO IMAGE_TAG POSTGRES_USER POSTGRES_PASSWORD RABBITMQ_USER RABBITMQ_PASSWORD
export CF_DNS_API_TOKEN GRAFANA_ADMIN_USER GRAFANA_ADMIN_PASSWORD N8N_DB_USER N8N_DB_PASSWORD
export AUTH_DB_USER AUTH_DB_PASSWORD ASSISTANT_DB_USER ASSISTANT_DB_PASSWORD
export STALWART_ADMIN_USER STALWART_ADMIN_PASSWORD GHCR_USER GHCR_TOKEN
export N8N_OAUTH_CLIENT_SECRET GRAFANA_OAUTH_CLIENT_SECRET VAULT_OIDC_CLIENT_SECRET

render_template "$RENDERED"

ok "Rendered $RENDERED"

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

info "Reinstalling VPS..."
cntb reinstall instance "$CONTABO_INSTANCE_ID" \
  --imageId "$CONTABO_IMAGE_ID" \
  --sshKeys "$CONTABO_SSH_SECRET_ID" \
  --userData "$(cat "$RENDERED")" \
  --defaultUser root \
  "${CNTB_AUTH[@]}"

echo ""
ok "VPS reinstall initiated."
echo ""
info "Next steps:"
echo "  1. Wait for cloud-init to finish cloning the repo, installing Nomad/Vault, and deploying jobs"
echo "  2. SSH in:  ssh -i ~/.ssh/personal-stack-root-user -p 2222 deploy@<VPS_IP>"
echo "  3. Check:   sudo nomad status"
echo "  4. Review:  sudo tail -n 200 /var/log/personal-stack-bootstrap.log"
