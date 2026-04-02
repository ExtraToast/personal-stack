{{ with secret "database/creds/n8n" }}
DB_POSTGRESDB_USER={{ .Data.username }}
DB_POSTGRESDB_PASSWORD={{ .Data.password }}
{{ end }}
OIDC_CLIENT_SECRET={{ with secret "secret/data/platform/automation" }}{{ index .Data.data "n8n.oauth_client_secret" }}{{ end }}
