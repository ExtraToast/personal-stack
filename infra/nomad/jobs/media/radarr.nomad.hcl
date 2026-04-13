variable "domain" {
  type    = string
  default = "jorisjonkers.dev"
}

job "radarr" {
  datacenters = ["dc1"]
  type        = "service"

  update {
    max_parallel      = 1
    min_healthy_time  = "10s"
    healthy_deadline  = "5m"
    progress_deadline = "10m"
    auto_revert       = true
  }

  group "radarr" {
    constraint {
      attribute = "${meta.node_type}"
      value     = "home"
    }

    network {
      mode = "host"
      port "http" { static = 7878 }
    }

    volume "media_data" {
      type      = "host"
      source    = "media_data"
      read_only = false
    }

    volume "radarr_config" {
      type      = "host"
      source    = "radarr_config"
      read_only = false
    }

    service {
      name = "radarr"
      port = "http"
      tags = [
        "traefik.enable=true",
        "traefik.http.routers.radarr.rule=Host(`radarr.${var.domain}`)",
        "traefik.http.routers.radarr.entrypoints=websecure",
        "traefik.http.routers.radarr.tls=true",
        "traefik.http.routers.radarr.middlewares=forward-auth@file,media-security-headers@file",
      ]

      check {
        type     = "http"
        path     = "/ping"
        interval = "15s"
        timeout  = "5s"
      }
    }

    task "radarr" {
      driver = "docker"

      env {
        PUID = "1000"
        PGID = "1000"
        TZ   = "Europe/Amsterdam"
      }

      config {
        image        = "linuxserver/radarr:5.21.1"
        network_mode = "host"
      }

      volume_mount {
        volume      = "media_data"
        destination = "/media"
      }

      volume_mount {
        volume      = "radarr_config"
        destination = "/config"
      }

      resources {
        cpu        = 1200
        memory     = 1280
        memory_max = 2048
      }
    }
  }
}
