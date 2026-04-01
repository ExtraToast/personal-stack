job "valkey" {
  datacenters = ["dc1"]
  type        = "service"

  group "valkey" {
    network {
      mode = "host"
      port "db" { static = 6379 }
    }

    volume "valkey_data" {
      type      = "host"
      source    = "valkey_data"
      read_only = false
    }

    service {
      name = "valkey"
      port = "db"

      check {
        type     = "tcp"
        interval = "15s"
        timeout  = "5s"
      }
    }

    task "valkey" {
      driver = "docker"

      config {
        image        = "valkey/valkey:8-alpine"
        network_mode = "host"
      }

      volume_mount {
        volume      = "valkey_data"
        destination = "/data"
      }

      resources {
        cpu    = 400
        memory = 512
      }
    }
  }
}
