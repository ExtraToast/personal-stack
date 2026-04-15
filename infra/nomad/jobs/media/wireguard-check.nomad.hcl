job "wireguard-check" {
  datacenters = ["dc1"]
  type        = "service"

  update {
    max_parallel      = 1
    min_healthy_time  = "10s"
    healthy_deadline  = "5m"
    progress_deadline = "10m"
    auto_revert       = true
  }

  group "wireguard-check" {
    constraint {
      attribute = "${meta.node_type}"
      value     = "home"
    }

    network {
      mode = "bridge"
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
        destination = "secrets/gluetun-wireguard.env"
        env         = true
        change_mode = "restart"
        data        = file("infra/nomad/templates/gluetun-wireguard.env.tpl")
      }

      driver = "docker"

      env {
        TZ                          = "Europe/Amsterdam"
        UPDATER_PERIOD              = "24h"
        HEALTH_VPN_DURATION_INITIAL = "30s"
      }

      config {
        image = "qmcgaw/gluetun:latest"
        cap_add = [
          "NET_ADMIN",
          "NET_RAW",
        ]
      }

      resources {
        cpu        = 500
        memory     = 256
        memory_max = 512
      }
    }

    task "curl" {
      driver = "docker"

      config {
        image   = "curlimages/curl:latest"
        command = "sh"
        args = [
          "-ec",
          "trap 'exit 0' TERM INT; while :; do date -Iseconds; curl -fsS https://ifconfig.me/ip; echo; sleep 300; done",
        ]
      }

      resources {
        cpu    = 200
        memory = 64
      }
    }
  }
}
