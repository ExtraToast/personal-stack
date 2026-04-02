datacenter = "dc1"
data_dir   = "/opt/nomad"
bind_addr  = "0.0.0.0"
region     = "global"

ui {
  enabled = true
}

server {
  enabled          = true
  bootstrap_expect = 1
}

client {
  enabled = true

  host_volume "postgres_data" {
    path      = "/srv/nomad/postgres"
    read_only = false
  }

  host_volume "prometheus_data" {
    path      = "/srv/nomad/prometheus"
    read_only = false
  }

  host_volume "traefik_data" {
    path      = "/srv/nomad/traefik"
    read_only = false
  }

  host_volume "valkey_data" {
    path      = "/srv/nomad/valkey"
    read_only = false
  }

  host_volume "rabbitmq_data" {
    path      = "/srv/nomad/rabbitmq"
    read_only = false
  }

  host_volume "n8n_data" {
    path      = "/srv/nomad/n8n"
    read_only = false
  }

  host_volume "grafana_data" {
    path      = "/srv/nomad/grafana"
    read_only = false
  }

  host_volume "loki_data" {
    path      = "/srv/nomad/loki"
    read_only = false
  }

  host_volume "tempo_data" {
    path      = "/srv/nomad/tempo"
    read_only = false
  }

  host_volume "uptime_kuma_data" {
    path      = "/srv/nomad/uptime-kuma"
    read_only = false
  }

  host_volume "stalwart_data" {
    path      = "/srv/nomad/stalwart"
    read_only = false
  }
}

plugin "docker" {
  config {
    volumes {
      enabled = true
    }
    allow_privileged = false
  }
}

acl {
  enabled = true
}

consul {
  address = "127.0.0.1:8500"
}

vault {
  enabled               = true
  address               = "http://127.0.0.1:8200"
  jwt_auth_backend_path = "jwt-nomad"

  default_identity {
    aud = ["vault.io"]
    ttl = "1h"
  }
}
