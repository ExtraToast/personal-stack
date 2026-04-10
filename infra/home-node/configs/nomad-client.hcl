datacenter = "dc1"
data_dir   = "/opt/nomad"
bind_addr  = "__TAILSCALE_IP__"
region     = "global"

server {
  enabled = false
}

client {
  enabled = true
  servers = ["__VPS_TAILSCALE_IP__:4647"]

  meta {
    node_type = "home"
    gpu       = "gtx960m"
  }

  host_volume "lightrag_data" {
    path      = "/srv/nomad/lightrag"
    read_only = false
  }

  host_volume "alloy_data" {
    path      = "/srv/nomad/alloy"
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
  address               = "http://__VPS_TAILSCALE_IP__:8200"
  jwt_auth_backend_path = "jwt-nomad"

  default_identity {
    aud = ["vault.io"]
    ttl = "1h"
  }
}
