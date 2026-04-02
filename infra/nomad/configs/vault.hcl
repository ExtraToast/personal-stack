ui            = true
disable_mlock = true

storage "raft" {
  path    = "/opt/vault/data"
  node_id = "personal-stack-vault-1"
}

# Listen on all interfaces so Docker containers (which reach the host via
# host.docker.internal -> primary IP) can connect. Public access is blocked
# by UFW; only loopback, docker0, and the primary NIC are reachable.
listener "tcp" {
  address         = "0.0.0.0:8200"
  cluster_address = "0.0.0.0:8201"
  tls_disable     = 1
}

api_addr     = "http://127.0.0.1:8200"
cluster_addr = "http://127.0.0.1:8201"
