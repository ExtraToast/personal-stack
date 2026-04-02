variable "domain" {
  type    = string
  default = "jorisjonkers.dev"
}

variable "image_tag" {
  type    = string
  default = "latest"
}

variable "image_repo" {
  type    = string
  default = "ghcr.io/extratoast/personal-stack"
}

variable "count" {
  type    = number
  default = 2
}

job "assistant-api" {
  datacenters = ["dc1"]
  type        = "service"

  update {
    max_parallel      = 1
    min_healthy_time  = "10s"
    healthy_deadline  = "5m"
    progress_deadline = "10m"
    auto_revert       = true
  }

  group "assistant-api" {
    count = var.count

    network {
      mode = "host"
      port "http" {}
    }

    service {
      name = "assistant-api"
      port = "http"
      tags = [
        "traefik.enable=true",
        "traefik.http.routers.assistant-api.rule=Host(`assistant.${var.domain}`) && PathPrefix(`/api/`) && !PathPrefix(`/api/actuator/prometheus`) && !PathPrefix(`/api/actuator/metrics`)",
        "traefik.http.routers.assistant-api.entrypoints=websecure",
        "traefik.http.routers.assistant-api.tls=true",
        "traefik.http.routers.assistant-api.middlewares=forward-auth@file,security-headers@file,rate-limit@file",
      ]

      check {
        type     = "http"
        path     = "/api/actuator/health/liveness"
        interval = "15s"
        timeout  = "5s"
      }
    }

    task "assistant-api" {
      vault {
        role        = "assistant-api"
        change_mode = "noop"
      }

      template {
        destination = "secrets/assistant-api.env"
        env         = true
        change_mode = "restart"
        data        = file("infra/nomad/templates/assistant-api.env.tpl")
      }

      driver = "docker"

      env {
        SERVER_PORT                 = "${NOMAD_PORT_http}"
        SPRING_PROFILES_ACTIVE      = "prod"
        SPRING_CONFIG_IMPORT        = "vault://"
        VAULT_ENABLED               = "true"
        VAULT_ADDR                  = "http://127.0.0.1:8200"
        VAULT_DB_ENABLED            = "true"
        DB_HOST                     = "127.0.0.1"
        DB_PORT                     = "5432"
        VALKEY_HOST                 = "127.0.0.1"
        VALKEY_PORT                 = "6379"
        SPRING_RABBITMQ_HOST        = "127.0.0.1"
        SPRING_RABBITMQ_PORT        = "5672"
        MAIL_HOST                   = "mail.${var.domain}"
        MAIL_PORT                   = "587"
        OTEL_SERVICE_NAME           = "assistant-api"
        OTEL_EXPORTER_OTLP_ENDPOINT = "http://127.0.0.1:4318"
        OTEL_EXPORTER_OTLP_PROTOCOL = "http/protobuf"
        OTEL_LOGS_EXPORTER          = "none"
        OTEL_METRICS_EXPORTER       = "none"
        OTEL_TRACES_EXPORTER        = "otlp"
      }

      config {
        image        = "${var.image_repo}/assistant-api:${var.image_tag}"
        network_mode = "host"
      }

      resources {
        cpu    = 800
        memory = 1024
      }
    }
  }
}
