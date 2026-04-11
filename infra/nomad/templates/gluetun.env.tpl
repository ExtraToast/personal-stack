{{ with secret "secret/data/platform/media" }}
VPN_SERVICE_PROVIDER=private internet access
VPN_TYPE=openvpn
OPENVPN_USER={{ index .Data.data "pia.username" }}
OPENVPN_PASSWORD={{ index .Data.data "pia.password" }}
SERVER_REGIONS={{ index .Data.data "pia.server_regions" }}
FIREWALL_OUTBOUND_SUBNETS=100.64.0.0/10,192.168.0.0/16,172.16.0.0/12
FIREWALL_INPUT_PORTS=8080,9696
{{ end }}
