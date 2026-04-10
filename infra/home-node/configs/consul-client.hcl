datacenter  = "dc1"
data_dir    = "/opt/consul"
bind_addr   = "__TAILSCALE_IP__"
client_addr = "127.0.0.1"

server = false

retry_join = ["__VPS_TAILSCALE_IP__"]

encrypt = "__CONSUL_ENCRYPT_KEY__"
