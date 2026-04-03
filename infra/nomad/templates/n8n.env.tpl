{{ with secret "secret/data/platform/postgres" }}
DB_POSTGRESDB_USER={{ index .Data.data "n8n.user" }}
DB_POSTGRESDB_PASSWORD={{ index .Data.data "n8n.password" }}
{{ end }}
OIDC_CLIENT_SECRET={{ with secret "secret/data/platform/automation" }}{{ index .Data.data "n8n.oauth_client_secret" }}{{ end }}
{{ with secret "secret/data/platform/automation" }}
GEMINI_API_KEY={{ index .Data.data "n8n.gemini_api_key" }}
SCRAPERAPI_KEY={{ index .Data.data "n8n.scraperapi_key" }}
{{ end }}
