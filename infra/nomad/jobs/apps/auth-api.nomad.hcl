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
  default = 1
}

job "auth-api" {
  datacenters = ["dc1"]
  type        = "service"

  update {
    max_parallel      = 1
    min_healthy_time  = "10s"
    healthy_deadline  = "8m"
    progress_deadline = "12m"
    auto_revert       = true
  }

  group "auth-api" {
    count = var.count

    constraint {
      attribute = "${meta.node_type}"
      value     = "cloud"
    }

    network {
      mode = "host"
      port "http" {}
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
        AUTH_LOGIN_URL              = "https://auth.${var.domain}/login"
        AUTH_ISSUER                 = "https://auth.${var.domain}"
        SESSION_COOKIE_DOMAIN       = "${var.domain}"
        SESSION_COOKIE_SECURE       = "true"
        AUTH_CORS_ALLOWED_ORIGINS   = "https://${var.domain},https://auth.${var.domain},https://assistant.${var.domain},https://vault.${var.domain},https://n8n.${var.domain},https://grafana.${var.domain},https://nomad.${var.domain},https://rabbitmq.${var.domain},https://stalwart.${var.domain},https://traefik.${var.domain},https://status.${var.domain},https://jellyfin.${var.domain},https://sonarr.${var.domain},https://radarr.${var.domain},https://prowlarr.${var.domain},https://qbittorrent.${var.domain},https://adguard.${var.domain},https://headscale.${var.domain}"
        DEPLOYMENT_ENVIRONMENT      = "production"
        SERVICE_VERSION             = var.image_tag
        OTEL_SERVICE_NAME           = "auth-api"
        OTEL_RESOURCE_ATTRIBUTES    = "deployment.environment=production,service.version=${var.image_tag}"
        OTEL_EXPORTER_OTLP_ENDPOINT = "http://127.0.0.1:4319"
        OTEL_EXPORTER_OTLP_PROTOCOL = "http/protobuf"
        OTEL_LOGS_EXPORTER          = "none"
        OTEL_METRICS_EXPORTER       = "none"
        OTEL_TRACES_EXPORTER        = "otlp"
      }

      config {
        image        = "${var.image_repo}/auth-api:${var.image_tag}"
        network_mode = "host"
      }

      resources {
        cpu        = 800
        memory     = 1280
        memory_max = 1536
      }
    }
  }
}
