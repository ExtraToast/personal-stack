variable "domain" {
  type    = string
  default = "jorisjonkers.dev"
}

job "downloads" {
  datacenters = ["dc1"]
  type        = "service"

  update {
    max_parallel      = 1
    min_healthy_time  = "30s"
    healthy_deadline  = "10m"
    progress_deadline = "15m"
    auto_revert       = true
  }

  group "downloads" {
    constraint {
      attribute = "${meta.node_type}"
      value     = "home"
    }

    network {
      mode = "bridge"
      port "qbt_web" {
        static = 8080
        to     = 8080
      }
      port "prowlarr" {
        static = 9696
        to     = 9696
      }
    }

    volume "media_data" {
      type      = "host"
      source    = "media_data"
      read_only = false
    }

    volume "qbittorrent_config" {
      type      = "host"
      source    = "qbittorrent_config"
      read_only = false
    }

    volume "prowlarr_config" {
      type      = "host"
      source    = "prowlarr_config"
      read_only = false
    }

    service {
      name = "qbittorrent"
      port = "qbt_web"
      tags = [
        "traefik.enable=true",
        "traefik.http.routers.qbittorrent.rule=Host(`qbittorrent.${var.domain}`)",
        "traefik.http.routers.qbittorrent.entrypoints=websecure",
        "traefik.http.routers.qbittorrent.tls=true",
        "traefik.http.routers.qbittorrent.middlewares=forward-auth@file,media-security-headers@file",
      ]

      check {
        type     = "http"
        path     = "/"
        interval = "30s"
        timeout  = "10s"
      }
    }

    service {
      name = "prowlarr"
      port = "prowlarr"
      tags = [
        "traefik.enable=true",
        "traefik.http.routers.prowlarr.rule=Host(`prowlarr.${var.domain}`)",
        "traefik.http.routers.prowlarr.entrypoints=websecure",
        "traefik.http.routers.prowlarr.tls=true",
        "traefik.http.routers.prowlarr.middlewares=forward-auth@file,media-security-headers@file",
      ]

      check {
        type     = "http"
        path     = "/ping"
        interval = "30s"
        timeout  = "10s"
      }
    }

    task "gluetun" {
      lifecycle {
        hook    = "prestart"
        sidecar = true
      }

      vault {
        role        = "downloads"
        change_mode = "noop"
      }

      template {
        destination = "secrets/gluetun.env"
        env         = true
        change_mode = "restart"
        data        = file("infra/nomad/templates/gluetun.env.tpl")
      }

      driver = "docker"

      env {
        TZ                          = "Europe/Amsterdam"
        UPDATER_PERIOD              = "24h"
        HEALTH_VPN_DURATION_INITIAL = "30s"
        FIREWALL_VPN_INPUT_PORTS    = "8080,9696"
      }

      config {
        image = "qmcgaw/gluetun:v3.40"
        cap_add = [
          "NET_ADMIN",
          "NET_RAW",
        ]
      }

      resources {
        cpu        = 400
        memory     = 768
        memory_max = 1024
      }
    }

    task "tailscale-local-route" {
      lifecycle {
        hook = "poststart"
      }

      driver = "docker"

      config {
        image   = "busybox:1.37"
        command = "sh"
        args = [
          "-ec",
          "gw=$(ip route | awk '$1 == \"default\" && $5 == \"eth0\" { print $3; exit }'); [ -n \"$gw\" ]; ip route replace 100.64.0.0/10 via \"$gw\" dev eth0; ip route | grep '^100\\.64\\.0\\.0/10 '",
        ]
        cap_add = [
          "NET_ADMIN",
        ]
      }

      resources {
        cpu    = 100
        memory = 64
      }
    }

    task "qbittorrent" {
      driver = "docker"

      env {
        PUID       = "1000"
        PGID       = "1000"
        TZ         = "Europe/Amsterdam"
        WEBUI_PORT = "8080"
      }

      config {
        image = "linuxserver/qbittorrent:5.0.4"
      }

      volume_mount {
        volume      = "media_data"
        destination = "/media"
      }

      volume_mount {
        volume      = "qbittorrent_config"
        destination = "/config"
      }

      resources {
        cpu        = 1500
        memory     = 1536
        memory_max = 2048
      }
    }

    task "prowlarr" {
      driver = "docker"

      env {
        PUID = "1000"
        PGID = "1000"
        TZ   = "Europe/Amsterdam"
      }

      config {
        image = "linuxserver/prowlarr:1.32.2"
      }

      volume_mount {
        volume      = "prowlarr_config"
        destination = "/config"
      }

      resources {
        cpu        = 500
        memory     = 768
        memory_max = 1024
      }
    }
  }
}
