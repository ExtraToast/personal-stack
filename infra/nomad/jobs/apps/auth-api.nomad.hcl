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

job "auth-api" {
  datacenters = ["dc1"]
  type        = "service"

  update {
    max_parallel      = 1
    min_healthy_time  = "10s"
    healthy_deadline  = "5m"
    progress_deadline = "10m"
    auto_revert       = true
  }

  group "auth-api" {
    count = 2

    network {
      port "http" { to = 8081 }
    }

    service {
      name = "auth-api"
      port = "http"
      tags = [
        "traefik.enable=true",
        "traefik.http.routers.auth-api.rule=Host(`auth.${var.domain}`) && PathPrefix(`/api/`) && !PathPrefix(`/api/actuator/prometheus`) && !PathPrefix(`/api/actuator/metrics`)",
        "traefik.http.routers.auth-api.entrypoints=websecure",
        "traefik.http.routers.auth-api.tls=true",
        "traefik.http.routers.auth-api.middlewares=security-headers@file,rate-limit@file",
        "traefik.http.routers.auth-api.service=auth-api",
        "traefik.http.routers.auth-api-well-known.rule=Host(`auth.${var.domain}`) && PathPrefix(`/.well-known/`)",
        "traefik.http.routers.auth-api-well-known.entrypoints=websecure",
        "traefik.http.routers.auth-api-well-known.tls=true",
        "traefik.http.routers.auth-api-well-known.middlewares=security-headers@file",
        "traefik.http.routers.auth-api-well-known.service=auth-api",
        "traefik.http.services.auth-api.loadbalancer.server.port=8081",
      ]

      check {
        type     = "http"
        path     = "/api/actuator/health/liveness"
        interval = "15s"
        timeout  = "5s"
      }
    }

    task "auth-api" {
      vault {
        role        = "auth-api"
        change_mode = "noop"
      }

      template {
        destination = "secrets/auth-api.env"
        env         = true
        change_mode = "restart"
        data        = file("infra/nomad/templates/auth-api.env.tpl")
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
        AUTH_LOGIN_URL              = "https://auth.${var.domain}/login"
        AUTH_ISSUER                 = "https://auth.${var.domain}"
        SESSION_COOKIE_DOMAIN       = "${var.domain}"
        SESSION_COOKIE_SECURE       = "true"
        AUTH_CORS_ALLOWED_ORIGINS   = "https://${var.domain},https://auth.${var.domain},https://assistant.${var.domain},https://vault.${var.domain},https://n8n.${var.domain},https://grafana.${var.domain},https://rabbitmq.${var.domain},https://stalwart.${var.domain},https://traefik.${var.domain},https://status.${var.domain}"
        OTEL_SERVICE_NAME           = "auth-api"
        OTEL_EXPORTER_OTLP_ENDPOINT = "http://${var.host_gateway}:4318"
        OTEL_EXPORTER_OTLP_PROTOCOL = "http/protobuf"
        OTEL_LOGS_EXPORTER          = "none"
        OTEL_METRICS_EXPORTER       = "none"
        OTEL_TRACES_EXPORTER        = "otlp"
      }

      config {
        image       = "${var.image_repo}/auth-api:${var.image_tag}"
        extra_hosts = ["${var.host_gateway}:host-gateway"]
      }

      resources {
        cpu    = 1200
        memory = 1024
      }
    }
  }
}
