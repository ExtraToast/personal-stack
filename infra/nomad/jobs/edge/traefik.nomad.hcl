variable "domain" {
  type    = string
  default = "jorisjonkers.dev"
}

variable "tls_mode" {
  type    = string
  default = "acme"
}

variable "tls_cert_dir" {
  type    = string
  default = "/srv/nomad/traefik/certs"
}

job "traefik" {
  datacenters = ["dc1"]
  type        = "service"

  update {
    min_healthy_time  = "10s"
    healthy_deadline  = "5m"
    progress_deadline = "10m"
    auto_revert       = true
  }

  group "traefik" {
    constraint {
      attribute = "${meta.node_type}"
      value     = "cloud"
    }

    network {
      mode = "host"
      port "http" { static = 80 }
      port "https" { static = 443 }
      port "admin" { static = 8088 }
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
        change_mode = "noop"
      }

      template {
        destination = "secrets/traefik.env"
        env         = true
        change_mode = "restart"
        data        = file("infra/nomad/templates/traefik.env.tpl")
      }

      template {
        destination = "local/traefik-static.yml"
        change_mode = "noop"
        data        = file("infra/nomad/templates/traefik-static.yml.tpl")
      }

      template {
        destination = "local/traefik-dynamic.yml"
        change_mode = "noop"
        data        = file("infra/nomad/templates/traefik-dynamic.yml.tpl")
      }

      env {
        DOMAIN                   = var.domain
        TLS_MODE                 = var.tls_mode
        DEPLOYMENT_ENVIRONMENT   = "production"
        SERVICE_VERSION          = "3.6"
        OTEL_SERVICE_NAME        = "traefik"
        OTEL_RESOURCE_ATTRIBUTES = "deployment.environment=production,service.version=3.6"
      }

      driver = "docker"

      config {
        image        = "traefik:v3.6"
        network_mode = "host"
        args         = ["--configFile=/local/traefik-static.yml"]
        volumes      = ["${var.tls_cert_dir}:/certs:ro"]
      }

      volume_mount {
        volume      = "traefik_data"
        destination = "/letsencrypt"
      }

      resources {
        cpu        = 800
        memory     = 768
        memory_max = 1024
      }
    }
  }
}
