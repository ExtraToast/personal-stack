variable "domain" {
  type    = string
  default = "jorisjonkers.dev"
}

job "stalwart" {
  datacenters = ["dc1"]
  type        = "service"

  update {
    min_healthy_time  = "10s"
    healthy_deadline  = "5m"
    progress_deadline = "10m"
    auto_revert       = true
  }

  group "stalwart" {
    network {
      mode = "host"
      port "smtp" { static = 25 }
      port "pop3" { static = 110 }
      port "imap" { static = 143 }
      port "submissions" { static = 465 }
      port "submission" { static = 587 }
      port "imaptls" { static = 993 }
      port "pop3s" { static = 995 }
      port "sieve" { static = 4190 }
      port "http" { static = 8080 }
    }

    volume "stalwart_data" {
      type      = "host"
      source    = "stalwart_data"
      read_only = false
    }

    service {
      name = "stalwart"
      port = "http"
      tags = [
        "traefik.enable=true",
        "traefik.http.routers.stalwart.rule=Host(`stalwart.${var.domain}`)",
        "traefik.http.routers.stalwart.entrypoints=websecure",
        "traefik.http.routers.stalwart.tls=true",
        "traefik.http.routers.stalwart.middlewares=forward-auth@file,stalwart-security-headers@file",
        "traefik.http.services.stalwart.loadbalancer.server.port=8080",
      ]

      check {
        type     = "tcp"
        interval = "15s"
        timeout  = "5s"
      }
    }

    task "stalwart" {
      vault {
        role        = "stalwart"
        change_mode = "noop"
      }

      template {
        destination = "local/config.toml"
        change_mode = "restart"
        data        = file("infra/nomad/templates/stalwart-config.toml.tpl")
      }

      env {
        DOMAIN = var.domain
      }

      driver = "docker"

      config {
        image        = "stalwartlabs/stalwart:latest"
        network_mode = "host"
        entrypoint   = ["bash", "-ec"]
        args = [
          "mkdir -p /opt/stalwart/etc && cp /local/config.toml /opt/stalwart/etc/config.toml && exec /usr/local/bin/entrypoint.sh /usr/local/bin/stalwart",
        ]
      }

      volume_mount {
        volume      = "stalwart_data"
        destination = "/opt/stalwart"
      }

      resources {
        cpu    = 600
        memory = 512
      }
    }
  }
}
