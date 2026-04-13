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

job "assistant-ui" {
  datacenters = ["dc1"]
  type        = "service"

  update {
    max_parallel      = 1
    min_healthy_time  = "10s"
    healthy_deadline  = "5m"
    progress_deadline = "10m"
    auto_revert       = true
  }

  group "assistant-ui" {
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
      name = "assistant-ui"
      port = "http"
      tags = [
        "traefik.enable=true",
        "traefik.http.routers.assistant-ui.rule=Host(`assistant.${var.domain}`) && !PathPrefix(`/api/`)",
        "traefik.http.routers.assistant-ui.entrypoints=websecure",
        "traefik.http.routers.assistant-ui.tls=true",
        "traefik.http.routers.assistant-ui.middlewares=rate-limit@file,security-headers@file",
      ]

      check {
        type     = "http"
        path     = "/"
        interval = "15s"
        timeout  = "5s"
      }
    }

    task "assistant-ui" {
      driver = "docker"

      config {
        image        = "${var.image_repo}/assistant-ui:${var.image_tag}"
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
        cpu        = 200
        memory     = 128
        memory_max = 192
      }
    }
  }
}
