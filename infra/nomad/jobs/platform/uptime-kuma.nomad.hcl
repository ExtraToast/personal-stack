variable "domain" {
  type    = string
  default = "jorisjonkers.dev"
}

job "uptime-kuma" {
  datacenters = ["dc1"]
  type        = "service"

  group "uptime-kuma" {
    constraint {
      attribute = "${meta.node_type}"
      value     = "cloud"
    }

    network {
      mode = "host"
      port "http" { static = 3001 }
    }

    volume "uptime_kuma_data" {
      type      = "host"
      source    = "uptime_kuma_data"
      read_only = false
    }

    service {
      name = "uptime-kuma"
      port = "http"
      tags = [
        "traefik.enable=true",
        "traefik.http.routers.uptime-kuma.rule=Host(`status.${var.domain}`)",
        "traefik.http.routers.uptime-kuma.entrypoints=websecure",
        "traefik.http.routers.uptime-kuma.tls=true",
        "traefik.http.routers.uptime-kuma.middlewares=security-headers@file",
        "traefik.http.services.uptime-kuma.loadbalancer.server.port=3001",
      ]

      check {
        type     = "http"
        path     = "/"
        interval = "15s"
        timeout  = "5s"
      }
    }

    task "uptime-kuma" {
      driver = "docker"

      env {
        UPTIME_KUMA_DB_TYPE = "sqlite"
      }

      config {
        image        = "louislam/uptime-kuma:2"
        network_mode = "host"
      }

      volume_mount {
        volume      = "uptime_kuma_data"
        destination = "/app/data"
      }

      resources {
        cpu    = 300
        memory = 256
      }
    }
  }
}
