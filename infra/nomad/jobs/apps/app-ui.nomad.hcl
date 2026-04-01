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

job "app-ui" {
  datacenters = ["dc1"]
  type        = "service"

  update {
    max_parallel      = 1
    min_healthy_time  = "10s"
    healthy_deadline  = "5m"
    progress_deadline = "10m"
    auto_revert       = true
  }

  group "app-ui" {
    count = 2

    network {
      port "http" { to = 80 }
    }

    service {
      name = "app-ui"
      port = "http"
      tags = [
        "traefik.enable=true",
        "traefik.http.routers.app-ui.rule=Host(`${var.domain}`)",
        "traefik.http.routers.app-ui.entrypoints=websecure",
        "traefik.http.routers.app-ui.tls=true",
        "traefik.http.routers.app-ui.middlewares=rate-limit@file,security-headers@file",
        "traefik.http.services.app-ui.loadbalancer.server.port=80",
      ]

      check {
        type     = "http"
        path     = "/"
        interval = "15s"
        timeout  = "5s"
      }
    }

    task "app-ui" {
      driver = "docker"

      config {
        image = "${var.image_repo}/app-ui:${var.image_tag}"
      }

      resources {
        cpu    = 200
        memory = 128
      }
    }
  }
}
