#!/usr/bin/env bash
# Usage: ./infra/scripts/make-admin.sh [username] [email]
# Defaults to ExtraToast / joriswouterjonkers@gmail.com
set -euo pipefail

USERNAME="${1:-ExtraToast}"
EMAIL="${2:-joriswouterjonkers@gmail.com}"
DB="auth_db"

# Works for local docker-compose Postgres as well as the in-cluster
# postgres pod (auto-detected via `kubectl`).
CONTAINER=$(docker ps --format "{{.Names}}" | rg '^(personal-stack-postgres|postgres-)' | head -1)
if [[ -z "${CONTAINER}" ]]; then
  echo "Error: no running postgres container found locally" >&2
  exit 1
fi

SQL=$(cat <<EOF
DO \$\$
DECLARE
  v_id UUID;
BEGIN
  SELECT id INTO v_id FROM app_user WHERE username = '${USERNAME}' OR email = '${EMAIL}' LIMIT 1;

  IF v_id IS NULL THEN
    RAISE EXCEPTION 'User not found: username=% email=%', '${USERNAME}', '${EMAIL}';
  END IF;

  UPDATE app_user
  SET role            = 'ADMIN',
      email_confirmed = TRUE,
      updated_at      = NOW()
  WHERE id = v_id;

  -- Grant all service permissions (belt-and-suspenders; ADMIN bypasses checks anyway)
  INSERT INTO user_service_permissions (user_id, service)
  VALUES
    (v_id, 'VAULT'),
    (v_id, 'MAIL'),
    (v_id, 'N8N'),
    (v_id, 'GRAFANA'),
    (v_id, 'ASSISTANT'),
    (v_id, 'DASHBOARD'),
    (v_id, 'RABBITMQ'),
    (v_id, 'TRAEFIK'),
    (v_id, 'STATUS')
  ON CONFLICT DO NOTHING;

  RAISE NOTICE 'Done — user % (%) is now ADMIN', '${USERNAME}', v_id;
END;
\$\$;
EOF
)

echo "Promoting '${USERNAME}' / '${EMAIL}' to ADMIN in ${DB}..."
docker exec -i "${CONTAINER}" psql -U postgres -d "${DB}" -c "${SQL}"
echo "Done."
