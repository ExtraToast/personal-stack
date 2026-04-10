job "flaresolverr" {
  datacenters = ["dc1"]
  type        = "service"

  group "flaresolverr" {
    constraint {
      attribute = "${meta.node_type}"
      value     = "cloud"
    }

    network {
      mode = "host"
      port "http" { static = 8191 }
    }

    service {
      name = "flaresolverr"
      port = "http"

      check {
        type     = "http"
        path     = "/health"
        interval = "15s"
        timeout  = "5s"
      }
    }

    task "flaresolverr" {
      driver = "docker"

      env {
        LOG_LEVEL = "info"
        TZ        = "Europe/Amsterdam"
      }

      config {
        image        = "ghcr.io/flaresolverr/flaresolverr:v3.3.21"
        network_mode = "host"
      }

      resources {
        cpu    = 500
        memory = 512
      }
    }
  }
}
