variable "domain" {
  type    = string
  default = "jorisjonkers.dev"
}

job "sonarr" {
  datacenters = ["dc1"]
  type        = "service"

  update {
    max_parallel      = 1
    min_healthy_time  = "10s"
    healthy_deadline  = "5m"
    progress_deadline = "10m"
    auto_revert       = true
  }

  group "sonarr" {
    constraint {
      attribute = "${meta.node_type}"
      value     = "home"
    }

    network {
      mode = "host"
      port "http" { static = 8989 }
    }

    volume "media_data" {
      type      = "host"
      source    = "media_data"
      read_only = false
    }

    volume "sonarr_config" {
      type      = "host"
      source    = "sonarr_config"
      read_only = false
    }

    service {
      name = "sonarr"
      port = "http"
      tags = [
        "traefik.enable=true",
        "traefik.http.routers.sonarr.rule=Host(`sonarr.${var.domain}`)",
        "traefik.http.routers.sonarr.entrypoints=websecure",
        "traefik.http.routers.sonarr.tls=true",
        "traefik.http.routers.sonarr.middlewares=forward-auth@file,media-security-headers@file",
      ]

      check {
        type     = "http"
        path     = "/ping"
        interval = "15s"
        timeout  = "5s"
      }
    }

    task "sonarr" {
      driver = "docker"

      env {
        PUID = "1000"
        PGID = "1000"
        TZ   = "Europe/Amsterdam"
      }

      config {
        image        = "linuxserver/sonarr:4.0.14"
        network_mode = "host"
      }

      volume_mount {
        volume      = "media_data"
        destination = "/media"
      }

      volume_mount {
        volume      = "sonarr_config"
        destination = "/config"
      }

      resources {
        cpu        = 1500
        memory     = 768
        memory_max = 1536
      }
    }
  }
}
