variable "domain" {
  type    = string
  default = "jorisjonkers.dev"
}

job "jellyseerr" {
  datacenters = ["dc1"]
  type        = "service"

  update {
    max_parallel      = 1
    min_healthy_time  = "10s"
    healthy_deadline  = "10m"
    progress_deadline = "15m"
    auto_revert       = true
  }

  group "jellyseerr" {
    constraint {
      attribute = "${meta.node_type}"
      value     = "home"
    }

    network {
      mode = "host"
      port "http" { static = 5055 }
    }

    volume "jellyseerr_config" {
      type      = "host"
      source    = "jellyseerr_config"
      read_only = false
    }

    service {
      name = "jellyseerr"
      port = "http"
      tags = [
        "traefik.enable=true",
        "traefik.http.routers.jellyseerr.rule=Host(`jellyseerr.${var.domain}`)",
        "traefik.http.routers.jellyseerr.entrypoints=websecure",
        "traefik.http.routers.jellyseerr.tls=true",
        "traefik.http.routers.jellyseerr.middlewares=media-security-headers@file",
      ]

      check {
        type     = "http"
        path     = "/api/v1/settings/public"
        interval = "15s"
        timeout  = "5s"
      }
    }

    task "jellyseerr" {
      driver = "docker"

      env {
        TZ   = "Europe/Amsterdam"
        PORT = "5055"
      }

      config {
        image        = "ghcr.io/seerr-team/seerr:latest"
        network_mode = "host"
      }

      volume_mount {
        volume      = "jellyseerr_config"
        destination = "/app/config"
      }

      resources {
        cpu        = 1000
        memory     = 1024
        memory_max = 1536
      }
    }
  }
}
