variable "domain" {
  type    = string
  default = "jorisjonkers.dev"
}

variable "repo_dir" {
  type    = string
  default = "/opt/personal-stack"
}

job "n8n" {
  datacenters = ["dc1"]
  type        = "service"

  update {
    max_parallel      = 1
    min_healthy_time  = "10s"
    healthy_deadline  = "10m"
    progress_deadline = "15m"
    auto_revert       = true
  }

  group "n8n" {
    network {
      mode = "host"
      port "http" { static = 5678 }
    }

    volume "n8n_data" {
      type      = "host"
      source    = "n8n_data"
      read_only = false
    }

    service {
      name = "n8n"
      port = "http"
      tags = [
        "traefik.enable=true",
        "traefik.http.routers.n8n.rule=Host(`n8n.${var.domain}`)",
        "traefik.http.routers.n8n.entrypoints=websecure",
        "traefik.http.routers.n8n.tls=true",
        "traefik.http.routers.n8n.middlewares=rate-limit@file,n8n-security-headers@file",
        "traefik.http.services.n8n.loadbalancer.server.port=5678",
      ]

      check {
        type     = "http"
        path     = "/healthz"
        interval = "15s"
        timeout  = "5s"
      }
    }

    task "n8n" {
      vault {
        role        = "n8n"
        change_mode = "restart"
      }

      template {
        destination = "secrets/n8n.env"
        env         = true
        change_mode = "restart"
        data        = file("infra/nomad/templates/n8n.env.tpl")
      }

      driver = "docker"

      env {
        DB_TYPE                      = "postgresdb"
        DB_POSTGRESDB_HOST           = "127.0.0.1"
        DB_POSTGRESDB_PORT           = "5432"
        DB_POSTGRESDB_DATABASE       = "n8n_db"
        N8N_PROTOCOL                 = "https"
        N8N_HOST                     = "n8n.${var.domain}"
        N8N_EDITOR_BASE_URL          = "https://n8n.${var.domain}"
        N8N_TRUST_PROXY              = "true"
        WEBHOOK_URL                  = "https://n8n.${var.domain}/"
        EXTERNAL_HOOK_FILES          = "/data/n8n/hooks.js"
        EXTERNAL_FRONTEND_HOOKS_URLS = "/assets/oidc-frontend-hook.js"
        N8N_ADDITIONAL_NON_UI_ROUTES = "auth"
        OIDC_ISSUER_URL              = "https://auth.${var.domain}"
        OIDC_CLIENT_ID               = "n8n"
        OIDC_REDIRECT_URI            = "https://n8n.${var.domain}/auth/oidc/callback"
        OIDC_SCOPES                  = "openid email profile"
      }

      config {
        image        = "n8nio/n8n:2.13.4"
        network_mode = "host"
        volumes = [
          "${var.repo_dir}/infra/n8n/hooks.js:/data/n8n/hooks.js:ro",
        ]
      }

      volume_mount {
        volume      = "n8n_data"
        destination = "/home/node/.n8n"
      }

      resources {
        cpu    = 700
        memory = 1024
      }
    }
  }
}
