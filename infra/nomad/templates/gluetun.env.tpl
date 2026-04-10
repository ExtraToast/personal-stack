{{ with secret "secret/data/platform/media" }}
VPN_SERVICE_PROVIDER=private internet access
VPN_TYPE=wireguard
{{ with index .Data.data "pia.wireguard_private_key" }}{{ if . }}WIREGUARD_PRIVATE_KEY={{ . }}{{ end }}{{ end }}
OPENVPN_USER={{ index .Data.data "pia.username" }}
OPENVPN_PASSWORD={{ index .Data.data "pia.password" }}
SERVER_REGIONS={{ index .Data.data "pia.server_regions" }}
{{ end }}
