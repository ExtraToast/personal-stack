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

job "auth-ui" {
  datacenters = ["dc1"]
  type        = "service"

  update {
    max_parallel      = 1
    min_healthy_time  = "10s"
    healthy_deadline  = "5m"
    progress_deadline = "10m"
    auto_revert       = true
  }

  group "auth-ui" {
    count = 2

    network {
      port "http" { to = 80 }
    }

    service {
      name = "auth-ui"
      port = "http"
      tags = [
        "traefik.enable=true",
        "traefik.http.routers.auth-ui.rule=Host(`auth.${var.domain}`) && !PathPrefix(`/api/`) && !PathPrefix(`/.well-known/`)",
        "traefik.http.routers.auth-ui.entrypoints=websecure",
        "traefik.http.routers.auth-ui.tls=true",
        "traefik.http.routers.auth-ui.middlewares=rate-limit@file,security-headers@file",
      ]

      check {
        type     = "http"
        path     = "/"
        interval = "15s"
        timeout  = "5s"
      }
    }

    task "auth-ui" {
      driver = "docker"

      config {
        image = "${var.image_repo}/auth-ui:${var.image_tag}"
      }

      resources {
        cpu    = 200
        memory = 128
      }
    }
  }
}
