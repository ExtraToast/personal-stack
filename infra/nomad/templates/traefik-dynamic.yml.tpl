{{ $domain := env "DOMAIN" }}http:
  routers:
    vault:
      rule: 'Host(`vault.{{ $domain }}`)'
      entryPoints:
        - websecure
      service: vault
      middlewares:
        - rate-limit
        - security-headers
{{ if ne (env "TLS_MODE") "file" }}
      tls:
        certResolver: cloudflare
{{ else }}
      tls: {}
{{ end }}

    traefik-dashboard:
      rule: 'Host(`traefik.{{ $domain }}`)'
      entryPoints:
        - websecure
      service: api@internal
      middlewares:
        - forward-auth
        - rate-limit
        - security-headers
{{ if ne (env "TLS_MODE") "file" }}
      tls:
        certResolver: cloudflare
{{ else }}
      tls: {}
{{ end }}

  middlewares:
    forward-auth:
      forwardAuth:
        address: 'https://auth.{{ $domain }}/api/v1/auth/verify'
        trustForwardHeader: true
        authResponseHeaders:
          - 'X-User-Id'
          - 'X-User-Email'
          - 'X-User-Roles'

    rate-limit:
      rateLimit:
        average: 100
        burst: 200

    security-headers:
      headers: &base-headers
        stsSeconds: 31536000
        stsIncludeSubdomains: true
        stsPreload: true
        frameDeny: true
        contentTypeNosniff: true
        referrerPolicy: 'strict-origin-when-cross-origin'
        contentSecurityPolicy: "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https://*.{{ $domain }} https://*.jorisjonkers.test; font-src 'self'; connect-src 'self' https://*.{{ $domain }} https://*.jorisjonkers.test; frame-ancestors 'none'"
        customResponseHeaders:
          X-Frame-Options: 'DENY'

    stalwart-security-headers:
      headers:
        <<: *base-headers
        contentSecurityPolicy: "default-src 'self'; script-src 'self' 'unsafe-inline' 'wasm-unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https://*.{{ $domain }} https://*.jorisjonkers.test; font-src 'self'; connect-src 'self' https://*.{{ $domain }} https://*.jorisjonkers.test; frame-ancestors 'none'"

    n8n-security-headers:
      headers:
        <<: *base-headers
        contentSecurityPolicy: "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval' https://cdn-rs.n8n.io; style-src 'self' 'unsafe-inline'; img-src 'self' data: https://*.{{ $domain }} https://*.jorisjonkers.test; font-src 'self'; connect-src 'self' https://*.{{ $domain }} https://*.jorisjonkers.test https://api.n8n.io https://ph.n8n.io; frame-ancestors 'none'"

    grafana-security-headers:
      headers:
        <<: *base-headers
        contentSecurityPolicy: "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https://*.{{ $domain }} https://*.jorisjonkers.test; font-src 'self'; connect-src 'self' https://*.{{ $domain }} https://*.jorisjonkers.test; frame-ancestors 'none'"

  services:
    vault:
      loadBalancer:
        servers:
          - url: 'http://127.0.0.1:8200'
