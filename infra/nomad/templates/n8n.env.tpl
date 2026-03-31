DB_POSTGRESDB_USER={{ with secret "secret/data/platform/automation" }}{{ index .Data.data "n8n.db_user" }}{{ end }}
DB_POSTGRESDB_PASSWORD={{ with secret "secret/data/platform/automation" }}{{ index .Data.data "n8n.db_password" }}{{ end }}
OIDC_CLIENT_SECRET={{ with secret "secret/data/platform/automation" }}{{ index .Data.data "n8n.oauth_client_secret" }}{{ end }}
