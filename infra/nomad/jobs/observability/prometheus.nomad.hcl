job "prometheus" {
  datacenters = ["dc1"]
  type        = "service"

  group "prometheus" {
    constraint {
      attribute = "${meta.node_type}"
      value     = "cloud"
    }

    network {
      mode = "host"
      port "http" { static = 9090 }
    }

    volume "prometheus_data" {
      type      = "host"
      source    = "prometheus_data"
      read_only = false
    }

    service {
      name = "prometheus"
      port = "http"

      check {
        type     = "http"
        path     = "/-/healthy"
        interval = "15s"
        timeout  = "5s"
      }
    }

    task "prometheus" {
      template {
        destination = "local/prometheus.yml"
        change_mode = "restart"
        data        = file("infra/nomad/templates/prometheus.yml.tpl")
      }

      driver = "docker"

      config {
        image        = "prom/prometheus:v3.3.1"
        network_mode = "host"
        args = [
          "--config.file=/local/prometheus.yml",
          "--storage.tsdb.retention.time=30d",
          "--web.enable-remote-write-receiver",
        ]
      }

      volume_mount {
        volume      = "prometheus_data"
        destination = "/prometheus"
      }

      resources {
        cpu        = 500
        memory     = 640
        memory_max = 896
      }
    }
  }
}
