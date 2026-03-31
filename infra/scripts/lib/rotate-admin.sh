#!/usr/bin/env bash
# rotate-admin.sh — Rotate admin passwords for Grafana, Stalwart, and PostgreSQL superuser.

rotate_admin_passwords() {
  local dry_run="${DRY_RUN:-false}"

  info "Rotating admin passwords..."

  if [[ "$dry_run" == "true" ]]; then
    ok "[DRY RUN] Would rotate: Grafana admin, Stalwart admin, Postgres superuser"
    return 0
  fi

  # ── Grafana admin ──────────────────────────────────────────────────────────
  local grafana_pass
  grafana_pass=$(openssl rand -base64 24 | tr -d '/+=' | head -c 32)

  # Read current admin user (preserve it)
  local grafana_container grafana_user
  grafana_container=$(docker ps --filter "name=personal-stack_grafana" --format "{{.ID}}" | head -1)
  if [[ -n "$grafana_container" ]]; then
    grafana_user=$(docker exec "$grafana_container" cat /run/secrets/grafana_admin_user 2>/dev/null) || grafana_user="admin"
  else
    grafana_user="admin"
  fi

  create_versioned_secret "grafana_admin_user" "$grafana_user"
  create_versioned_secret "grafana_admin_password" "$grafana_pass"
  ok "Grafana admin password rotated"

  # ── Stalwart admin ─────────────────────────────────────────────────────────
  local stalwart_user stalwart_pass
  stalwart_user="${STALWART_ADMIN_USER:-admin}"
  stalwart_pass=$(openssl rand -base64 24 | tr -d '/+=' | head -c 32)

  create_versioned_secret "stalwart_admin_user" "$stalwart_user"
  create_versioned_secret "stalwart_admin_password" "$stalwart_pass"
  ok "Stalwart admin password rotated"

  # Persist to app-secrets file
  if [[ -f "$APP_SECRETS_FILE" ]]; then
    if grep -q '^STALWART_ADMIN_PASSWORD=' "$APP_SECRETS_FILE" 2>/dev/null; then
      sed -i "s/^STALWART_ADMIN_PASSWORD=.*/STALWART_ADMIN_PASSWORD=${stalwart_pass}/" "$APP_SECRETS_FILE"
    else
      printf '\nSTALWART_ADMIN_USER=%s\nSTALWART_ADMIN_PASSWORD=%s\n' \
        "$stalwart_user" "$stalwart_pass" >> "$APP_SECRETS_FILE"
    fi
  fi

  # ── PostgreSQL superuser ───────────────────────────────────────────────────
  local pg_container pg_user new_pg_pass
  pg_container=$(docker ps --filter "name=personal-stack_postgres" --format "{{.ID}}" | head -1)
  if [[ -n "$pg_container" ]]; then
    pg_user=$(docker exec "$pg_container" cat /run/secrets/postgres_user 2>/dev/null) || pg_user="postgres"
    new_pg_pass=$(openssl rand -base64 24 | tr -d '/+=' | head -c 32)

    docker exec "$pg_container" psql -U "$pg_user" -d postgres \
      -c "ALTER USER ${pg_user} WITH PASSWORD '${new_pg_pass}';" > /dev/null 2>&1 \
      || die "Failed to change PostgreSQL superuser password"

    create_versioned_secret "postgres_user" "$pg_user"
    create_versioned_secret "postgres_password" "$new_pg_pass"
    ok "PostgreSQL superuser password rotated"
  else
    warn "PostgreSQL container not found -- skipping superuser rotation"
  fi

  ROTATION_RESULTS+=("admin:success")
}
