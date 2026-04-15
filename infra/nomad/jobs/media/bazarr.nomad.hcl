variable "domain" {
  type    = string
  default = "jorisjonkers.dev"
}

job "bazarr" {
  datacenters = ["dc1"]
  type        = "service"

  update {
    max_parallel      = 1
    min_healthy_time  = "10s"
    healthy_deadline  = "5m"
    progress_deadline = "10m"
    auto_revert       = true
  }

  group "bazarr" {
    constraint {
      attribute = "${meta.node_type}"
      value     = "home"
    }

    network {
      mode = "host"
      port "http" { static = 6767 }
    }

    volume "media_data" {
      type      = "host"
      source    = "media_data"
      read_only = false
    }

    volume "bazarr_config" {
      type      = "host"
      source    = "bazarr_config"
      read_only = false
    }

    service {
      name = "bazarr"
      port = "http"
      tags = [
        "traefik.enable=true",
        "traefik.http.routers.bazarr.rule=Host(`bazarr.${var.domain}`)",
        "traefik.http.routers.bazarr.entrypoints=websecure",
        "traefik.http.routers.bazarr.tls=true",
        "traefik.http.routers.bazarr.middlewares=forward-auth@file,media-security-headers@file",
      ]

      check {
        type     = "http"
        path     = "/"
        interval = "15s"
        timeout  = "5s"
      }
    }

    task "bazarr" {
      driver = "docker"

      env {
        PUID = "1000"
        PGID = "1000"
        TZ   = "Europe/Amsterdam"
      }

      config {
        image        = "linuxserver/bazarr:latest"
        network_mode = "host"
      }

      volume_mount {
        volume      = "media_data"
        destination = "/media"
      }

      volume_mount {
        volume      = "bazarr_config"
        destination = "/config"
      }

      resources {
        cpu        = 1000
        memory     = 768
        memory_max = 1536
      }
    }
  }
}
