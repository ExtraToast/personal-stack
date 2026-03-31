job "stalwart" {
  datacenters = ["dc1"]
  type        = "service"

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
        "traefik.http.routers.stalwart.rule=Host(`stalwart.jorisjonkers.dev`)",
        "traefik.http.routers.stalwart.entrypoints=websecure",
        "traefik.http.routers.stalwart.tls=true",
        "traefik.http.routers.stalwart.middlewares=forward-auth@file,stalwart-security-headers@file",
        "traefik.http.services.stalwart.loadbalancer.server.port=8080",
      ]

      check {
        type     = "tcp"
        interval = "30s"
        timeout  = "10s"
      }
    }

    task "stalwart" {
      vault {
        role        = "stalwart"
        change_mode = "restart"
      }

      template {
        destination = "local/config.toml"
        change_mode = "restart"
        data        = file("infra/nomad/templates/stalwart-config.toml.tpl")
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
        cpu    = 900
        memory = 1024
      }
    }
  }
}
