#!/usr/bin/env bash
# rotate-approle.sh — Rotate Vault AppRole secret_ids for all services.
#
# Generates new secret_ids, creates versioned Docker Swarm secrets, and
# triggers a rolling redeploy via docker stack deploy with the compose overlay.

rotate_approle_credentials() {
  local dry_run="${DRY_RUN:-false}"

  info "Rotating AppRole secret_ids..."

  for role in auth-api assistant-api; do
    local secret_id role_id swarm_name

    role_id=$(vault_exec read -field=role_id "auth/approle/role/${role}/role-id")

    if [[ "$dry_run" == "true" ]]; then
      ok "[DRY RUN] Would rotate secret_id for ${role} (role_id: $(mask "$role_id"))"
      continue
    fi

    # Generate a new secret_id
    secret_id=$(vault_exec write -f -field=secret_id "auth/approle/role/${role}/secret-id")
    ok "${role}: new secret_id generated"

    # Create versioned Swarm secrets
    swarm_name=$(echo "$role" | tr '-' '_')
    create_versioned_secret "vault_${swarm_name}_role_id" "$role_id"
    create_versioned_secret "vault_${swarm_name}_secret_id" "$secret_id"
    ok "${role}: versioned Swarm secrets created"
  done

  if [[ "$dry_run" != "true" ]]; then
    ROTATION_RESULTS+=("approle:success")
  fi
}
