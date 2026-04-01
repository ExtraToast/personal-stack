GF_SECURITY_ADMIN_USER={{ with secret "secret/data/platform/observability" }}{{ index .Data.data "grafana.admin_user" }}{{ end }}
GF_SECURITY_ADMIN_PASSWORD={{ with secret "secret/data/platform/observability" }}{{ index .Data.data "grafana.admin_password" }}{{ end }}
GF_AUTH_GENERIC_OAUTH_CLIENT_SECRET={{ with secret "secret/data/platform/observability" }}{{ index .Data.data "grafana.oauth_client_secret" }}{{ end }}
