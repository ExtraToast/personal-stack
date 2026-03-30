storage "raft" {
  path = "/vault/data"
  node_id = "node1"
}

listener "tcp" {
  address     = "0.0.0.0:8200"
  tls_disable = 1
}

ui = true
disable_mlock = true
# Vault UI OIDC requests must reflect the public address served through Traefik.
api_addr = "https://vault.jorisjonkers.dev"
cluster_addr = "http://vault:8201"
