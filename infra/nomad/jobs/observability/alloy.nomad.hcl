job "alloy" {
  datacenters = ["dc1"]
  type        = "system"

  group "alloy" {
    network {
      mode = "host"
      port "http" { static = 12345 }
      port "otlp_http" { static = 4319 }
      port "otlp_grpc" { static = 4320 }
    }

    volume "alloy_data" {
      type      = "host"
      source    = "alloy_data"
      read_only = false
    }

    service {
      name = "alloy"
      port = "http"

      check {
        type     = "http"
        path     = "/-/ready"
        interval = "15s"
        timeout  = "5s"
      }
    }

    task "alloy" {
      template {
        destination = "local/config.alloy"
        change_mode = "restart"
        data        = file("infra/nomad/templates/alloy.config.alloy.tpl")
      }

      driver = "docker"

      config {
        image        = "grafana/alloy:v1.12.1"
        network_mode = "host"
        args = [
          "run",
          "--server.http.listen-addr=0.0.0.0:12345",
          "--storage.path=/var/lib/alloy/data",
          "--disable-reporting",
          "/local/config.alloy",
        ]
        volumes = [
          "/var/run/docker.sock:/var/run/docker.sock:ro",
        ]
      }

      volume_mount {
        volume      = "alloy_data"
        destination = "/var/lib/alloy/data"
      }

      resources {
        cpu    = 300
        memory     = 256
        memory_max = 384
      }
    }
  }
}
