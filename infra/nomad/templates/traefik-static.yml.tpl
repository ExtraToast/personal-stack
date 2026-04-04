entryPoints:
  web:
    address: ':80'
    http:
      redirections:
        entryPoint:
          to: websecure
          scheme: https
  websecure:
    address: ':443'
{{ if eq (env "TLS_MODE") "file" }}
    forwardedHeaders:
      trustedIPs:
        - '127.0.0.1/32'
        - '::1/128'
{{ end }}
{{ if eq (env "TLS_MODE") "file" }}
    http:
      tls: {}
{{ else }}
    http:
      tls:
        certResolver: cloudflare
{{ end }}
  traefik:
    address: ':8088'

{{ if ne (env "TLS_MODE") "file" }}
certificatesResolvers:
  cloudflare:
    acme:
      email: admin@{{ env "DOMAIN" }}
      storage: /letsencrypt/acme.json
      dnsChallenge:
        provider: cloudflare
        resolvers:
          - '1.1.1.1:53'
          - '1.0.0.1:53'
{{ end }}

providers:
  file:
    filename: /local/traefik-dynamic.yml
  consulCatalog:
    endpoint:
      address: 127.0.0.1:8500
    exposedByDefault: false

api:
  dashboard: true

metrics:
  prometheus:
    addRoutersLabels: true

accessLog:
  filters:
    statusCodes:
      - '400-599'

tracing:
  otlp:
    http:
      endpoint: http://127.0.0.1:4318/v1/traces

ping:
  entryPoint: traefik

log:
  level: INFO
