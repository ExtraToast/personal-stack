#!/usr/bin/env bash
# rotate-rabbitmq.sh — Rotate RabbitMQ password without data loss.
#
# Uses rabbitmqctl change_password (no volume wipe), updates Vault KV,
# and creates versioned Swarm secrets.

rotate_rabbitmq_credentials() {
  local dry_run="${DRY_RUN:-false}"

  info "Rotating RabbitMQ password..."

  local rmq_container rmq_user new_pass
  rmq_container=$(docker ps --filter "name=personal-stack_rabbitmq" --format "{{.ID}}" | head -1)
  [[ -n "$rmq_container" ]] || die "RabbitMQ container not running."

  # Read current username
  rmq_user=$(docker exec "$rmq_container" cat /run/secrets/rabbitmq_user 2>/dev/null) \
    || die "Could not read rabbitmq_user secret."

  if [[ "$dry_run" == "true" ]]; then
    ok "[DRY RUN] Would rotate password for RabbitMQ user '${rmq_user}'"
    return 0
  fi

  # Generate new password
  new_pass=$(openssl rand -base64 24 | tr -d '/+=' | head -c 32)

  # Change password in RabbitMQ (no restart needed, no data loss)
  docker exec "$rmq_container" rabbitmqctl change_password "$rmq_user" "$new_pass" > /dev/null 2>&1 \
    || die "Failed to change RabbitMQ password via rabbitmqctl"
  ok "RabbitMQ password changed via rabbitmqctl"

  # Update Vault KV so Spring APIs pick up new password on restart
  vault_exec kv patch secret/auth-api "spring.rabbitmq.password=${new_pass}" > /dev/null
  vault_exec kv patch secret/assistant-api "spring.rabbitmq.password=${new_pass}" > /dev/null
  ok "Vault KV updated (secret/auth-api, secret/assistant-api)"

  # Create versioned Swarm secrets
  create_versioned_secret "rabbitmq_user" "$rmq_user"
  create_versioned_secret "rabbitmq_password" "$new_pass"
  ok "Versioned Swarm secrets created"

  # Update app secrets file
  if [[ -f "$APP_SECRETS_FILE" ]]; then
    sed -i "s/^RABBITMQ_PASSWORD=.*/RABBITMQ_PASSWORD=${new_pass}/" "$APP_SECRETS_FILE"
    ok "App secrets file updated"
  fi

  ROTATION_RESULTS+=("rabbitmq:success")
}
