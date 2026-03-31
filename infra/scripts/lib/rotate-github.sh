#!/usr/bin/env bash
# rotate-github.sh — Rotate GitHub-stored secrets (SSH deploy key, etc.).
#
# Requires:
#   - `gh` CLI installed and authenticated (with admin:public_key and repo scopes)
#   - SSH access to the server (runs locally on the server)

GITHUB_REPO="${GITHUB_REPO:-ExtraToast/personal-stack}"

rotate_github_secrets() {
  local dry_run="${DRY_RUN:-false}"

  info "Rotating GitHub secrets..."

  # Check prerequisites
  if ! command -v gh > /dev/null 2>&1; then
    warn "gh CLI not found -- skipping GitHub secret rotation"
    ROTATION_RESULTS+=("github:skipped")
    return 0
  fi

  if ! gh auth status > /dev/null 2>&1; then
    warn "gh CLI not authenticated -- skipping GitHub secret rotation"
    ROTATION_RESULTS+=("github:skipped")
    return 0
  fi

  if [[ "$dry_run" == "true" ]]; then
    ok "[DRY RUN] Would rotate SSH deploy key and DEPLOY_SSH_KEY GitHub secret"
    return 0
  fi

  # ── Rotate SSH deploy key ──────────────────────────────────────────────────
  local key_dir key_file
  key_dir=$(mktemp -d)
  key_file="${key_dir}/deploy_key"

  ssh-keygen -t ed25519 -C "deploy@personal-stack-$(date +%Y%m%d)" \
    -f "$key_file" -N "" -q

  # Update GitHub Actions secret
  gh secret set DEPLOY_SSH_KEY \
    --body "$(cat "$key_file")" \
    --repo "$GITHUB_REPO" > /dev/null 2>&1 \
    || die "Failed to set DEPLOY_SSH_KEY in GitHub"
  ok "GitHub secret DEPLOY_SSH_KEY updated"

  # Update server authorized_keys
  local deploy_home="/home/deploy"
  if [[ -d "$deploy_home/.ssh" ]]; then
    local new_pub
    new_pub=$(cat "${key_file}.pub")

    # Only remove keys with the "deploy@personal-stack" comment — all other
    # keys (personal SSH keys, other services) are preserved untouched.
    {
      grep -v "deploy@personal-stack" "${deploy_home}/.ssh/authorized_keys" 2>/dev/null || true
      echo "$new_pub"
    } > "${deploy_home}/.ssh/authorized_keys.tmp"
    mv "${deploy_home}/.ssh/authorized_keys.tmp" "${deploy_home}/.ssh/authorized_keys"
    chmod 600 "${deploy_home}/.ssh/authorized_keys"
    chown deploy:deploy "${deploy_home}/.ssh/authorized_keys"
    ok "Server authorized_keys updated"
  else
    warn "Deploy user home not found at ${deploy_home} -- update authorized_keys manually"
  fi

  # Clean up temp files
  rm -rf "$key_dir"

  ROTATION_RESULTS+=("github:success")
}
