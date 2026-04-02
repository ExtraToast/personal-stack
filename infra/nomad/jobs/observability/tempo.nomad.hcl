job "tempo" {
  datacenters = ["dc1"]
  type        = "service"

  group "tempo" {
    network {
      mode = "host"
      port "http" { static = 3200 }
      port "otlp_grpc" { static = 4317 }
      port "otlp_http" { static = 4318 }
    }

    volume "tempo_data" {
      type      = "host"
      source    = "tempo_data"
      read_only = false
    }

    service {
      name = "tempo"
      port = "http"

      check {
        type     = "http"
        path     = "/ready"
        interval = "15s"
        timeout  = "5s"
      }
    }

    task "tempo" {
      template {
        destination = "local/tempo.yml"
        change_mode = "restart"
        data        = file("infra/nomad/templates/tempo.yml.tpl")
      }

      driver = "docker"

      config {
        image        = "grafana/tempo:2.7.2"
        network_mode = "host"
        args         = ["-config.file=/local/tempo.yml"]
      }

      volume_mount {
        volume      = "tempo_data"
        destination = "/var/tempo"
      }

      resources {
        cpu    = 400
        memory = 512
      }
    }
  }
}
