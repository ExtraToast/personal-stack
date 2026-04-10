job "tempo" {
  datacenters = ["dc1"]
  type        = "service"

  group "tempo" {
    constraint {
      attribute = "${meta.node_type}"
      value     = "cloud"
    }

    network {
      mode = "host"
      port "http" { static = 3200 }
      port "grpc" { static = 9096 }
      port "memberlist" { static = 7946 }
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

    service {
      name = "tempo-otlp-http"
      port = "otlp_http"
    }

    task "tempo" {
      template {
        destination = "local/tempo.yml"
        change_mode = "restart"
        data        = file("infra/nomad/templates/tempo.yml.tpl")
      }

      driver = "docker"

      config {
        image        = "grafana/tempo:2.10.3"
        network_mode = "host"
        args         = ["-config.file=/local/tempo.yml"]
      }

      volume_mount {
        volume      = "tempo_data"
        destination = "/var/tempo"
      }

      resources {
        cpu    = 400
        memory = 1024
      }
    }
  }
}
