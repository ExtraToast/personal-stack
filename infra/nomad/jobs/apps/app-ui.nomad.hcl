variable "domain" {
  type    = string
  default = "jorisjonkers.dev"
}

variable "image_tag" {
  type    = string
  default = "latest"
}

variable "image_repo" {
  type    = string
  default = "ghcr.io/extratoast/personal-stack"
}

variable "count" {
  type    = number
  default = 1
}

job "app-ui" {
  datacenters = ["dc1"]
  type        = "service"

  update {
    max_parallel      = 1
    min_healthy_time  = "10s"
    healthy_deadline  = "5m"
    progress_deadline = "10m"
    auto_revert       = true
  }

  group "app-ui" {
    count = var.count

    constraint {
      attribute = "${meta.node_type}"
      value     = "cloud"
    }

    network {
      mode = "host"
      port "http" {}
    }

    service {
      name = "app-ui"
      port = "http"
      tags = [
        "traefik.enable=true",
        "traefik.http.routers.app-ui.rule=Host(`${var.domain}`)",
        "traefik.http.routers.app-ui.entrypoints=websecure",
        "traefik.http.routers.app-ui.tls=true",
        "traefik.http.routers.app-ui.middlewares=rate-limit@file,security-headers@file",
      ]

      check {
        type     = "http"
        path     = "/"
        interval = "15s"
        timeout  = "5s"
      }
    }

    task "app-ui" {
      driver = "docker"

      config {
        image        = "${var.image_repo}/app-ui:${var.image_tag}"
        network_mode = "host"
        args         = ["nginx", "-g", "daemon off;", "-c", "/local/nginx.conf"]
      }

      template {
        destination = "local/nginx.conf"
        data        = <<-EOT
          worker_processes auto;
          events { worker_connections 512; }
          http {
            include       /etc/nginx/mime.types;
            default_type  application/octet-stream;
            sendfile      on;
            server {
              listen {{ env "NOMAD_PORT_http" }};
              location / {
                root   /usr/share/nginx/html;
                index  index.html;
                try_files $uri $uri/ /index.html;
              }
            }
          }
        EOT
      }

      resources {
        cpu    = 200
        memory     = 128
        memory_max = 192
      }
    }
  }
}
