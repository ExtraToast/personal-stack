variable "domain" {
  type    = string
  default = "jorisjonkers.dev"
}

job "traefik" {
  datacenters = ["dc1"]
  type        = "service"

  group "traefik" {
    network {
      mode = "host"
      port "http" { static = 80 }
      port "https" { static = 443 }
      port "admin" { static = 8080 }
    }

    volume "traefik_data" {
      type      = "host"
      source    = "traefik_data"
      read_only = false
    }

    service {
      name = "traefik"
      port = "admin"

      check {
        type     = "http"
        path     = "/ping"
        interval = "15s"
        timeout  = "5s"
      }
    }

    task "traefik" {
      vault {
        role        = "traefik"
        change_mode = "restart"
      }

      template {
        destination = "secrets/traefik.env"
        env         = true
        change_mode = "restart"
        data        = file("infra/nomad/templates/traefik.env.tpl")
      }

      template {
        destination = "local/traefik-static.yml"
        change_mode = "restart"
        data        = file("infra/nomad/templates/traefik-static.yml.tpl")
      }

      template {
        destination = "local/traefik-dynamic.yml"
        change_mode = "restart"
        data        = file("infra/nomad/templates/traefik-dynamic.yml.tpl")
      }

      env {
        DOMAIN = var.domain
      }

      driver = "docker"

      config {
        image        = "traefik:v3.6"
        network_mode = "host"
        args = [
          "--configFile=/local/traefik-static.yml",
          "--providers.file.filename=/local/traefik-dynamic.yml",
          "--providers.consulcatalog=true",
          "--providers.consulcatalog.endpoint.address=127.0.0.1:8500",
          "--providers.consulcatalog.exposedbydefault=false",
        ]
      }

      volume_mount {
        volume      = "traefik_data"
        destination = "/letsencrypt"
      }

      resources {
        cpu    = 500
        memory = 512
      }
    }
  }
}
