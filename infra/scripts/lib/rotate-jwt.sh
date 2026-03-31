#!/usr/bin/env bash
# rotate-jwt.sh — Rotate the JWT RSA signing key with dual-key rollover.
#
# The current key becomes the "previous" key (kept for token verification),
# and a new key is generated for signing new tokens. After the maximum token
# lifetime (7 days for refresh tokens), the previous key can be removed.

rotate_jwt_signing_key() {
  local dry_run="${DRY_RUN:-false}"

  info "Rotating JWT signing key (dual-key rollover)..."

  if [[ "$dry_run" == "true" ]]; then
    ok "[DRY RUN] Would rotate JWT signing key with dual-key rollover"
    return 0
  fi

  # Read current key from Vault
  local current_key
  current_key=$(vault_exec kv get -field="auth.signing-key" secret/auth-api 2>/dev/null || true)
  if [[ -z "$current_key" ]]; then
    warn "No current signing key found -- generating fresh key without rollover"
  fi

  # Generate new RSA key
  local new_key
  new_key=$(openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 2>/dev/null)
  ok "New RSA 2048-bit signing key generated"

  # Read all other fields to preserve them
  local rmq_user rmq_pass grafana_secret n8n_secret vault_secret stalwart_secret
  rmq_user=$(vault_exec kv get -field="spring.rabbitmq.username" secret/auth-api 2>/dev/null || true)
  rmq_pass=$(vault_exec kv get -field="spring.rabbitmq.password" secret/auth-api 2>/dev/null || true)
  grafana_secret=$(vault_exec kv get -field="auth.clients.grafana.secret" secret/auth-api 2>/dev/null || true)
  n8n_secret=$(vault_exec kv get -field="auth.clients.n8n.secret" secret/auth-api 2>/dev/null || true)
  vault_secret=$(vault_exec kv get -field="auth.clients.vault.secret" secret/auth-api 2>/dev/null || true)
  stalwart_secret=$(vault_exec kv get -field="auth.clients.stalwart.secret" secret/auth-api 2>/dev/null || true)

  # Write: new key as current, old key as previous
  local kv_args=(
    "spring.rabbitmq.username=${rmq_user}"
    "spring.rabbitmq.password=${rmq_pass}"
    "auth.signing-key=${new_key}"
    "auth.clients.grafana.secret=${grafana_secret}"
    "auth.clients.n8n.secret=${n8n_secret}"
    "auth.clients.vault.secret=${vault_secret}"
    "auth.clients.stalwart.secret=${stalwart_secret}"
  )
  [[ -n "$current_key" ]] && kv_args+=("auth.signing-key-previous=${current_key}")

  vault_exec kv put secret/auth-api "${kv_args[@]}" > /dev/null
  ok "Vault KV updated: new signing key active, previous key retained for verification"

  info "The previous key will be valid for token verification until tokens expire"
  info "Remove 'auth.signing-key-previous' from Vault KV after 7 days (max refresh token TTL)"

  ROTATION_RESULTS+=("jwt:success")
}

# Remove the previous signing key after the rollover window has passed.
cleanup_previous_jwt_key() {
  local dry_run="${DRY_RUN:-false}"

  local previous_key
  previous_key=$(vault_exec kv get -field="auth.signing-key-previous" secret/auth-api 2>/dev/null || true)

  if [[ -z "$previous_key" ]]; then
    info "No previous JWT signing key to clean up"
    return 0
  fi

  if [[ "$dry_run" == "true" ]]; then
    ok "[DRY RUN] Would remove previous JWT signing key from Vault KV"
    return 0
  fi

  # Remove by patching (delete the field)
  vault_exec kv patch -delete "auth.signing-key-previous" secret/auth-api > /dev/null 2>&1 \
    || warn "Could not delete previous key field (may need manual cleanup)"
  ok "Previous JWT signing key removed from Vault KV"
}
