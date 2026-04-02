VAULT_AUTHENTICATION=TOKEN
SPRING_CLOUD_VAULT_TOKEN={{ env "VAULT_TOKEN" }}
{{ with secret "rabbitmq/creds/app-consumer" }}
SPRING_RABBITMQ_USERNAME={{ .Data.username }}
SPRING_RABBITMQ_PASSWORD={{ .Data.password }}
{{ end }}
