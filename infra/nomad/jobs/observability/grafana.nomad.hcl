variable "repo_dir" {
  type    = string
  default = "/opt/personal-stack"
}

job "grafana" {
  datacenters = ["dc1"]
  type        = "service"

  update {
    max_parallel      = 1
    min_healthy_time  = "10s"
    healthy_deadline  = "10m"
    progress_deadline = "15m"
    auto_revert       = true
  }

  group "grafana" {
    network {
      mode = "host"
      port "http" { static = 3000 }
    }

    volume "grafana_data" {
      type      = "host"
      source    = "grafana_data"
      read_only = false
    }

    service {
      name = "grafana"
      port = "http"
      tags = [
        "traefik.enable=true",
        "traefik.http.routers.grafana.rule=Host(`grafana.jorisjonkers.dev`)",
        "traefik.http.routers.grafana.entrypoints=websecure",
        "traefik.http.routers.grafana.tls=true",
        "traefik.http.routers.grafana.middlewares=grafana-security-headers@file",
        "traefik.http.services.grafana.loadbalancer.server.port=3000",
      ]

      check {
        type     = "http"
        path     = "/api/health"
        interval = "15s"
        timeout  = "5s"
      }
    }

    task "grafana" {
      vault {
        role        = "grafana"
        change_mode = "restart"
      }

      template {
        destination = "secrets/grafana.env"
        env         = true
        change_mode = "restart"
        data        = file("infra/nomad/templates/grafana.env.tpl")
      }

      driver = "docker"

      env {
        GF_SERVER_ROOT_URL                               = "https://grafana.jorisjonkers.dev"
        GF_AUTH_GENERIC_OAUTH_ENABLED                    = "true"
        GF_AUTH_GENERIC_OAUTH_NAME                       = "personal-stack"
        GF_AUTH_GENERIC_OAUTH_CLIENT_ID                  = "grafana"
        GF_AUTH_GENERIC_OAUTH_SCOPES                     = "openid profile email"
        GF_AUTH_GENERIC_OAUTH_AUTH_URL                   = "https://auth.jorisjonkers.dev/api/oauth2/authorize"
        GF_AUTH_GENERIC_OAUTH_TOKEN_URL                  = "https://auth.jorisjonkers.dev/api/oauth2/token"
        GF_AUTH_GENERIC_OAUTH_API_URL                    = "https://auth.jorisjonkers.dev/api/userinfo"
        GF_AUTH_GENERIC_OAUTH_LOGIN_ATTRIBUTE_PATH       = "preferred_username"
        GF_AUTH_GENERIC_OAUTH_NAME_ATTRIBUTE_PATH        = "name"
        GF_AUTH_GENERIC_OAUTH_EMAIL_ATTRIBUTE_PATH       = "email"
        GF_AUTH_GENERIC_OAUTH_ROLE_ATTRIBUTE_PATH        = "contains(roles[*], 'ROLE_ADMIN') && 'GrafanaAdmin' || 'Viewer'"
        GF_AUTH_GENERIC_OAUTH_ALLOW_ASSIGN_GRAFANA_ADMIN = "true"
        GF_AUTH_GENERIC_OAUTH_AUTO_LOGIN                 = "true"
        GF_AUTH_GENERIC_OAUTH_USE_PKCE                   = "true"
      }

      config {
        image        = "grafana/grafana:11.6.0"
        network_mode = "host"
        volumes = [
          "${var.repo_dir}/infra/observability/grafana/provisioning/datasources:/etc/grafana/provisioning/datasources:ro",
          "${var.repo_dir}/infra/observability/grafana/provisioning/dashboards:/etc/grafana/provisioning/dashboards:ro",
          "${var.repo_dir}/infra/observability/grafana/dashboards:/var/lib/grafana/dashboards:ro",
        ]
      }

      volume_mount {
        volume      = "grafana_data"
        destination = "/var/lib/grafana"
      }

      resources {
        cpu    = 700
        memory = 768
      }
    }
  }
}
