datacenter     = "dc1"
data_dir       = "/opt/consul"
bind_addr      = "0.0.0.0"
advertise_addr = "__ADVERTISE_ADDR__"
client_addr    = "127.0.0.1"

server           = true
bootstrap_expect = 1

encrypt = "__CONSUL_ENCRYPT_KEY__"

ui_config {
  enabled = true
}
