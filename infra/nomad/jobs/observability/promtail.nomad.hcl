job "promtail" {
  datacenters = ["dc1"]
  type        = "system"

  group "promtail" {
    network {
      mode = "host"
      port "http" { static = 9080 }
    }

    service {
      name = "promtail"
      port = "http"

      check {
        type     = "http"
        path     = "/ready"
        interval = "15s"
        timeout  = "5s"
      }
    }

    task "promtail" {
      template {
        destination = "local/promtail.yml"
        change_mode = "restart"
        data        = file("infra/nomad/templates/promtail.yml.tpl")
      }

      driver = "docker"

      config {
        image        = "grafana/promtail:3.5.0"
        network_mode = "host"
        args         = ["-config.file=/local/promtail.yml"]
        volumes = [
          "/var/log:/var/log:ro",
          "/var/run/docker.sock:/var/run/docker.sock:ro",
        ]
      }

      resources {
        cpu    = 300
        memory = 256
      }
    }
  }
}
