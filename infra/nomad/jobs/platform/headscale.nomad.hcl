variable "domain" {
  type    = string
  default = "jorisjonkers.dev"
}

job "headscale" {
  datacenters = ["dc1"]
  type        = "service"

  update {
    min_healthy_time  = "10s"
    healthy_deadline  = "5m"
    progress_deadline = "10m"
    auto_revert       = true
  }

  group "headscale" {
    constraint {
      attribute = "${meta.node_type}"
      value     = "cloud"
    }

    network {
      mode = "host"
      port "http" { static = 8085 }
    }

    volume "headscale_data" {
      type      = "host"
      source    = "headscale_data"
      read_only = false
    }

    service {
      name = "headscale"
      port = "http"
      tags = [
        "traefik.enable=true",
        "traefik.http.routers.headscale.rule=Host(`headscale.${var.domain}`)",
        "traefik.http.routers.headscale.entrypoints=websecure",
        "traefik.http.routers.headscale.tls=true",
        "traefik.http.services.headscale.loadbalancer.server.port=8085",
      ]

      check {
        type     = "http"
        path     = "/health"
        interval = "15s"
        timeout  = "5s"
      }
    }

    task "headscale" {
      vault {
        role        = "headscale"
        change_mode = "noop"
      }

      template {
        destination = "local/config.yaml"
        change_mode = "restart"
        data        = file("infra/nomad/templates/headscale.yml.tpl")
      }

      env {
        DOMAIN = var.domain
      }

      driver = "docker"

      config {
        image        = "headscale/headscale:0.25"
        network_mode = "host"
        args         = ["serve"]
        volumes = [
          "local/config.yaml:/etc/headscale/config.yaml:ro",
        ]
      }

      volume_mount {
        volume      = "headscale_data"
        destination = "/var/lib/headscale"
      }

      resources {
        cpu    = 200
        memory     = 256
        memory_max = 384
      }
    }
  }
}
