variable "domain" {
  type    = string
  default = "jorisjonkers.dev"
}

job "jellyfin" {
  datacenters = ["dc1"]
  type        = "service"

  update {
    max_parallel      = 1
    min_healthy_time  = "10s"
    healthy_deadline  = "5m"
    progress_deadline = "10m"
    auto_revert       = true
  }

  group "jellyfin" {
    constraint {
      attribute = "${meta.node_type}"
      value     = "home"
    }

    network {
      mode = "host"
      port "http" { static = 8096 }
    }

    volume "media_data" {
      type      = "host"
      source    = "media_data"
      read_only = true
    }

    volume "jellyfin_config" {
      type      = "host"
      source    = "jellyfin_config"
      read_only = false
    }

    service {
      name = "jellyfin"
      port = "http"
      tags = [
        "traefik.enable=true",
        "traefik.http.routers.jellyfin.rule=Host(`jellyfin.${var.domain}`)",
        "traefik.http.routers.jellyfin.entrypoints=websecure",
        "traefik.http.routers.jellyfin.tls=true",
        "traefik.http.routers.jellyfin.middlewares=forward-auth@file,security-headers@file",
      ]

      check {
        type     = "http"
        path     = "/health"
        interval = "15s"
        timeout  = "5s"
      }
    }

    task "jellyfin" {
      driver = "docker"

      env {
        PUID = "1000"
        PGID = "1000"
        TZ   = "Europe/Amsterdam"
      }

      config {
        image        = "jellyfin/jellyfin:10.10.6"
        network_mode = "host"
      }

      volume_mount {
        volume      = "media_data"
        destination = "/media"
        read_only   = true
      }

      volume_mount {
        volume      = "jellyfin_config"
        destination = "/config"
      }

      resources {
        cpu    = 2000
        memory = 2048
      }
    }
  }
}
