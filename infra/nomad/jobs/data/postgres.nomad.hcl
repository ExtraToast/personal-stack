variable "repo_dir" {
  type    = string
  default = "/opt/personal-stack"
}

job "postgres" {
  datacenters = ["dc1"]
  type        = "service"

  group "postgres" {
    network {
      mode = "host"
      port "db" { static = 5432 }
    }

    volume "postgres_data" {
      type      = "host"
      source    = "postgres_data"
      read_only = false
    }

    service {
      name = "postgres"
      port = "db"

      check {
        type     = "tcp"
        interval = "15s"
        timeout  = "5s"
      }
    }

    task "postgres" {
      vault {
        role        = "postgres"
        change_mode = "noop"
      }

      template {
        destination = "secrets/postgres.env"
        env         = true
        change_mode = "noop"
        data        = file("infra/nomad/templates/postgres.env.tpl")
      }

      driver = "docker"

      config {
        image        = "postgres:17-alpine"
        network_mode = "host"
        args         = ["-c", "listen_addresses=*"]
        volumes = [
          "${var.repo_dir}/infra/docker/postgres/init-databases.sh:/docker-entrypoint-initdb.d/init-databases.sh:ro",
        ]
      }

      volume_mount {
        volume      = "postgres_data"
        destination = "/var/lib/postgresql/data"
      }

      resources {
        cpu    = 800
        memory = 1024
      }
    }
  }
}
