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

variable "host_gateway" {
  type    = string
  default = "host.docker.internal"
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
    count = 2

    network {
      port "http" { to = 8082 }
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
        "traefik.http.services.assistant-api.loadbalancer.server.port=8082",
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
        SPRING_PROFILES_ACTIVE      = "prod"
        SPRING_CONFIG_IMPORT        = "vault://"
        VAULT_ENABLED               = "true"
        VAULT_ADDR                  = "http://${var.host_gateway}:8200"
        VAULT_DB_ENABLED            = "true"
        DB_HOST                     = var.host_gateway
        DB_PORT                     = "5432"
        VALKEY_HOST                 = var.host_gateway
        VALKEY_PORT                 = "6379"
        SPRING_RABBITMQ_HOST        = var.host_gateway
        SPRING_RABBITMQ_PORT        = "5672"
        MAIL_HOST                   = "mail.${var.domain}"
        MAIL_PORT                   = "587"
        OTEL_SERVICE_NAME           = "assistant-api"
        OTEL_EXPORTER_OTLP_ENDPOINT = "http://${var.host_gateway}:4318"
        OTEL_EXPORTER_OTLP_PROTOCOL = "http/protobuf"
        OTEL_LOGS_EXPORTER          = "none"
        OTEL_METRICS_EXPORTER       = "none"
        OTEL_TRACES_EXPORTER        = "otlp"
      }

      config {
        image       = "${var.image_repo}/assistant-api:${var.image_tag}"
        extra_hosts = ["${var.host_gateway}:host-gateway"]
      }

      resources {
        cpu    = 800
        memory = 1024
      }
    }
  }
}
