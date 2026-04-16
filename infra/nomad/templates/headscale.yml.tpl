server_url: https://headscale.{{ env "DOMAIN" }}
listen_addr: 0.0.0.0:{{ env "NOMAD_PORT_http" }}
metrics_listen_addr: 127.0.0.1:9098

database:
  type: sqlite
  sqlite:
    path: /var/lib/headscale/db.sqlite

noise:
  private_key_path: /var/lib/headscale/noise_private.key

prefixes:
  v4: 100.64.0.0/10
  v6: fd7a:115c:a1e0::/48
  allocation: sequential

dns:
  base_domain: tail.{{ env "DOMAIN" }}
  magic_dns: true
  nameservers:
    global:
      - 1.1.1.1
      - 8.8.8.8

derp:
  server:
    enabled: false
  urls:
    - https://controlplane.tailscale.com/derpmap/default
  auto_update_enabled: true
  update_frequency: 24h

disable_check_updates: true

log:
  level: info
