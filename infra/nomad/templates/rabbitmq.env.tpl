RABBITMQ_DEFAULT_USER={{ with secret "secret/data/platform/rabbitmq" }}{{ index .Data.data "rabbitmq.user" }}{{ end }}
RABBITMQ_DEFAULT_PASS={{ with secret "secret/data/platform/rabbitmq" }}{{ index .Data.data "rabbitmq.password" }}{{ end }}
