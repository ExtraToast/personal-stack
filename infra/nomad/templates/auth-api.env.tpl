VAULT_AUTHENTICATION=TOKEN
SPRING_CLOUD_VAULT_TOKEN={{ env "VAULT_TOKEN" }}
AUTH_TRANSIT_ENABLED=true
AUTH_TRANSIT_KEY_NAME=auth-api-jwt
AUTH_CLIENTS_GRAFANA_SECRET={{ with secret "secret/data/auth-api" }}{{ index .Data.data "auth.clients.grafana.secret" }}{{ end }}
AUTH_CLIENTS_N8N_SECRET={{ with secret "secret/data/auth-api" }}{{ index .Data.data "auth.clients.n8n.secret" }}{{ end }}
AUTH_CLIENTS_VAULT_SECRET={{ with secret "secret/data/auth-api" }}{{ index .Data.data "auth.clients.vault.secret" }}{{ end }}
MAIL_USERNAME={{ with secret "secret/data/auth-api" }}{{ index .Data.data "mail.username" }}{{ end }}
MAIL_PASSWORD={{ with secret "secret/data/auth-api" }}{{ index .Data.data "mail.password" }}{{ end }}
{{ with secret "rabbitmq/creds/app-consumer" }}
SPRING_RABBITMQ_USERNAME={{ .Data.username }}
SPRING_RABBITMQ_PASSWORD={{ .Data.password }}
{{ end }}
