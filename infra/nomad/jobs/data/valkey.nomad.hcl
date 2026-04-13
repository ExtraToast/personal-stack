job "valkey" {
  datacenters = ["dc1"]
  type        = "service"

  group "valkey" {
    constraint {
      attribute = "${meta.node_type}"
      value     = "cloud"
    }

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
        cpu        = 500
        memory     = 512
        memory_max = 768
      }
    }
  }
}
