#!/usr/bin/env bash
# rotate-oauth2.sh — Rotate OAuth2 client secrets for all registered clients.
#
# Generates new secrets, updates Vault KV (auth-api reads from there),
# updates Vault OIDC config, and creates versioned Swarm secrets for
# consumer services (Grafana, n8n, Stalwart).

rotate_oauth2_client_secrets() {
  local dry_run="${DRY_RUN:-false}"

  info "Rotating OAuth2 client secrets..."

  if [[ "$dry_run" == "true" ]]; then
    ok "[DRY RUN] Would rotate OAuth2 client secrets for: grafana, n8n, vault, stalwart"
    return 0
  fi

  # Generate new secrets
  local grafana_secret n8n_secret vault_secret stalwart_secret
  grafana_secret=$(openssl rand -base64 32 | tr -d '/+=' | head -c 40)
  n8n_secret=$(openssl rand -base64 32 | tr -d '/+=' | head -c 40)
  vault_secret=$(openssl rand -base64 32 | tr -d '/+=' | head -c 40)
  stalwart_secret=$(openssl rand -base64 32 | tr -d '/+=' | head -c 40)

  # Preserve non-rotated fields from Vault KV
  local signing_key rmq_user rmq_pass previous_key
  signing_key=$(vault_exec kv get -field="auth.signing-key" secret/auth-api 2>/dev/null || true)
  previous_key=$(vault_exec kv get -field="auth.signing-key-previous" secret/auth-api 2>/dev/null || true)
  rmq_user=$(vault_exec kv get -field="spring.rabbitmq.username" secret/auth-api 2>/dev/null || true)
  rmq_pass=$(vault_exec kv get -field="spring.rabbitmq.password" secret/auth-api 2>/dev/null || true)

  # Write all fields back (kv put replaces all data)
  local kv_args=(
    "spring.rabbitmq.username=${rmq_user}"
    "spring.rabbitmq.password=${rmq_pass}"
    "auth.signing-key=${signing_key}"
    "auth.clients.grafana.secret=${grafana_secret}"
    "auth.clients.n8n.secret=${n8n_secret}"
    "auth.clients.vault.secret=${vault_secret}"
    "auth.clients.stalwart.secret=${stalwart_secret}"
  )
  [[ -n "$previous_key" ]] && kv_args+=("auth.signing-key-previous=${previous_key}")

  vault_exec kv put secret/auth-api "${kv_args[@]}" > /dev/null
  ok "Vault KV secret/auth-api updated with new client secrets"

  # Update Vault OIDC config with the new vault client secret
  vault_exec write auth/oidc/config \
    oidc_client_secret="${vault_secret}" > /dev/null 2>&1 \
    || warn "Failed to update Vault OIDC client secret (may need manual update)"
  ok "Vault OIDC config updated"

  # Create versioned Swarm secrets for consumer services
  create_versioned_secret "oauth2_grafana_secret" "$grafana_secret"
  create_versioned_secret "oauth2_n8n_secret" "$n8n_secret"
  create_versioned_secret "oauth2_stalwart_secret" "$stalwart_secret"
  ok "Versioned Swarm secrets created for OAuth2 consumers"

  ROTATION_RESULTS+=("oauth2:success")
}
