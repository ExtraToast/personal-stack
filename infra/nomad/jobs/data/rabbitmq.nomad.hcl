variable "domain" {
  type    = string
  default = "jorisjonkers.dev"
}

variable "repo_dir" {
  type    = string
  default = "/opt/personal-stack"
}

variable "oidc_tls_skip_verify" {
  type    = bool
  default = false
}

job "rabbitmq" {
  datacenters = ["dc1"]
  type        = "service"

  group "rabbitmq" {
    network {
      mode = "host"
      port "amqp" { static = 5672 }
      port "management" { static = 15672 }
      port "metrics" { static = 15692 }
    }

    volume "rabbitmq_data" {
      type      = "host"
      source    = "rabbitmq_data"
      read_only = false
    }

    service {
      name = "rabbitmq"
      port = "management"
      tags = [
        "traefik.enable=true",
        "traefik.http.routers.rabbitmq.rule=Host(`rabbitmq.${var.domain}`)",
        "traefik.http.routers.rabbitmq.entrypoints=websecure",
        "traefik.http.routers.rabbitmq.tls=true",
        "traefik.http.routers.rabbitmq.middlewares=forward-auth@file,rabbitmq-security-headers@file",
        "traefik.http.services.rabbitmq.loadbalancer.server.port=15672",
      ]

      check {
        type     = "http"
        path     = "/"
        interval = "15s"
        timeout  = "5s"
      }
    }

    service {
      name = "rabbitmq-metrics"
      port = "metrics"

      check {
        type     = "tcp"
        interval = "15s"
        timeout  = "5s"
      }
    }

    task "rabbitmq" {
      vault {
        role        = "rabbitmq"
        change_mode = "noop"
      }

      template {
        destination = "local/rabbitmq.conf"
        change_mode = "restart"
        data        = file("infra/nomad/templates/rabbitmq.conf.tpl")
      }

      template {
        destination = "local/enabled_plugins"
        change_mode = "restart"
        data        = file("infra/rabbitmq/enabled_plugins")
      }

      template {
        destination = "secrets/rabbitmq.env"
        env         = true
        change_mode = "noop"
        data        = file("infra/nomad/templates/rabbitmq.env.tpl")
      }

      driver = "docker"

      env {
        DOMAIN                        = var.domain
        RABBITMQ_OIDC_TLS_SKIP_VERIFY = var.oidc_tls_skip_verify ? "true" : "false"
      }

      config {
        image        = "rabbitmq:4.2-management-alpine"
        network_mode = "host"
        volumes = [
          "local/rabbitmq.conf:/etc/rabbitmq/rabbitmq.conf:ro",
          "local/enabled_plugins:/etc/rabbitmq/enabled_plugins:ro",
        ]
      }

      volume_mount {
        volume      = "rabbitmq_data"
        destination = "/var/lib/rabbitmq"
      }

      resources {
        cpu    = 500
        memory = 384
      }
    }
  }
}
