{{ with secret "secret/data/platform/media" }}
VPN_SERVICE_PROVIDER=custom
VPN_TYPE=wireguard
FIREWALL_OUTBOUND_SUBNETS=100.64.0.0/10,192.168.0.0/16,172.16.0.0/12
{{ with index .Data.data "pia.wireguard_endpoint_ip" }}
VPN_ENDPOINT_IP={{ . }}
{{ end }}
{{ with index .Data.data "pia.wireguard_endpoint_port" }}
VPN_ENDPOINT_PORT={{ . }}
{{ end }}
{{ with index .Data.data "pia.wireguard_server_public_key" }}
WIREGUARD_PUBLIC_KEY={{ . }}
{{ end }}
{{ with index .Data.data "pia.wireguard_private_key" }}
WIREGUARD_PRIVATE_KEY={{ . }}
{{ end }}
{{ with index .Data.data "pia.wireguard_addresses" }}
WIREGUARD_ADDRESSES={{ . }}
{{ end }}
{{ with index .Data.data "pia.wireguard_preshared_key" }}
WIREGUARD_PRESHARED_KEY={{ . }}
{{ end }}
{{ with index .Data.data "pia.wireguard_dns" }}
VPN_DNS_ADDRESS={{ . }}
{{ end }}
{{ end }}
