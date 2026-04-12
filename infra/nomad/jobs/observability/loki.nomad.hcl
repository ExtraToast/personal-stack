variable "repo_dir" {
  type    = string
  default = "/opt/personal-stack"
}

job "loki" {
  datacenters = ["dc1"]
  type        = "service"

  group "loki" {
    constraint {
      attribute = "${meta.node_type}"
      value     = "cloud"
    }

    network {
      mode = "host"
      port "http" { static = 3100 }
    }

    volume "loki_data" {
      type      = "host"
      source    = "loki_data"
      read_only = false
    }

    service {
      name = "loki"
      port = "http"

      check {
        type     = "http"
        path     = "/ready"
        interval = "15s"
        timeout  = "5s"
      }
    }

    task "loki" {
      driver = "docker"

      config {
        image        = "grafana/loki:3.5.0"
        network_mode = "host"
        args         = ["-config.file=/etc/loki/loki.yaml"]
        volumes = [
          "${var.repo_dir}/infra/observability/loki/loki.yaml:/etc/loki/loki.yaml:ro",
        ]
      }

      volume_mount {
        volume      = "loki_data"
        destination = "/loki"
      }

      resources {
        cpu    = 400
        memory     = 640
        memory_max = 896
      }
    }
  }
}
