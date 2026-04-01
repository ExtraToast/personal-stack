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

job "assistant-ui" {
  datacenters = ["dc1"]
  type        = "service"

  update {
    max_parallel      = 1
    min_healthy_time  = "10s"
    healthy_deadline  = "5m"
    progress_deadline = "10m"
    auto_revert       = true
  }

  group "assistant-ui" {
    count = 2

    network {
      port "http" { to = 80 }
    }

    service {
      name = "assistant-ui"
      port = "http"
      tags = [
        "traefik.enable=true",
        "traefik.http.routers.assistant-ui.rule=Host(`assistant.${var.domain}`) && !PathPrefix(`/api/`)",
        "traefik.http.routers.assistant-ui.entrypoints=websecure",
        "traefik.http.routers.assistant-ui.tls=true",
        "traefik.http.routers.assistant-ui.middlewares=rate-limit@file,security-headers@file",
        "traefik.http.services.assistant-ui.loadbalancer.server.port=80",
      ]

      check {
        type     = "http"
        path     = "/"
        interval = "15s"
        timeout  = "5s"
      }
    }

    task "assistant-ui" {
      driver = "docker"

      config {
        image = "${var.image_repo}/assistant-ui:${var.image_tag}"
      }

      resources {
        cpu    = 500
        memory = 256
      }
    }
  }
}
