variable "repo_dir" {
  type    = string
  default = "/opt/personal-stack"
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
        "traefik.http.routers.rabbitmq.rule=Host(`rabbitmq.jorisjonkers.dev`)",
        "traefik.http.routers.rabbitmq.entrypoints=websecure",
        "traefik.http.routers.rabbitmq.tls=true",
        "traefik.http.routers.rabbitmq.middlewares=security-headers@file",
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
        change_mode = "restart"
      }

      template {
        destination = "secrets/rabbitmq.env"
        env         = true
        change_mode = "restart"
        data        = file("infra/nomad/templates/rabbitmq.env.tpl")
      }

      driver = "docker"

      config {
        image        = "rabbitmq:4.2-management-alpine"
        network_mode = "host"
        volumes = [
          "${var.repo_dir}/infra/rabbitmq/rabbitmq.prod.conf:/etc/rabbitmq/rabbitmq.conf:ro",
          "${var.repo_dir}/infra/rabbitmq/enabled_plugins:/etc/rabbitmq/enabled_plugins:ro",
        ]
      }

      volume_mount {
        volume      = "rabbitmq_data"
        destination = "/var/lib/rabbitmq"
      }

      resources {
        cpu    = 700
        memory = 768
      }
    }
  }
}
