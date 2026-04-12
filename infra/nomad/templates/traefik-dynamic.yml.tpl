{{ $domain := env "DOMAIN" }}{{ if eq (env "TLS_MODE") "file" }}
tls:
  certificates:
    - certFile: /certs/wildcard.crt
      keyFile: /certs/wildcard.key
{{ end }}
http:
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

    nomad:
      rule: 'Host(`nomad.{{ $domain }}`)'
      entryPoints:
        - websecure
      service: nomad
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

    adguard:
      rule: 'Host(`adguard.{{ $domain }}`)'
      entryPoints:
        - websecure
      service: adguard
      middlewares:
        - forward-auth
        - media-security-headers
{{ if ne (env "TLS_MODE") "file" }}
      tls:
        certResolver: cloudflare
{{ else }}
      tls: {}
{{ end }}

  middlewares:
    forward-auth:
      forwardAuth:
        # Call auth-api directly so the auth check keeps the original
        # X-Forwarded-* context instead of re-entering Traefik as auth.<domain>.
        address: '{{ with service "auth-api" }}{{ with index . 0 }}http://{{ .Address }}:{{ .Port }}/api/v1/auth/verify{{ end }}{{ else }}https://auth.{{ $domain }}/api/v1/auth/verify{{ end }}'
        trustForwardHeader: true
{{ if eq (env "TLS_MODE") "file" }}
        tls:
          ca: /certs/wildcard.crt
{{ end }}
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
        contentSecurityPolicy: "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval' https://cdn-rs.n8n.io; style-src 'self' 'unsafe-inline'; img-src 'self' data: https://*.{{ $domain }} https://*.jorisjonkers.test https://gravatar.com; font-src 'self' data:; connect-src 'self' https://*.{{ $domain }} https://*.jorisjonkers.test https://api.n8n.io https://ph.n8n.io https://api.github.com; frame-ancestors 'none'"

    rabbitmq-security-headers:
      headers:
        <<: *base-headers
        contentSecurityPolicy: "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https://*.{{ $domain }} https://*.jorisjonkers.test; font-src 'self'; connect-src 'self' https://*.{{ $domain }} https://*.jorisjonkers.test; frame-ancestors 'none'"

    grafana-security-headers:
      headers:
        <<: *base-headers
        contentSecurityPolicy: "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https://*.{{ $domain }} https://*.jorisjonkers.test; font-src 'self'; connect-src 'self' https://*.{{ $domain }} https://*.jorisjonkers.test; frame-ancestors 'none'"

    media-security-headers:
      headers:
        <<: *base-headers
        frameDeny: false
        contentSecurityPolicy: "default-src 'self' data: blob:; script-src 'self' 'unsafe-inline' 'unsafe-eval' blob: https://cdn.jsdelivr.net; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; img-src 'self' data: blob: https:; font-src 'self' data: https://fonts.gstatic.com; connect-src 'self' wss: https://*.{{ $domain }} https://*.jorisjonkers.test; frame-ancestors 'self'"

  services:
    vault:
      loadBalancer:
        servers:
          - url: 'http://127.0.0.1:8200'

    nomad:
      loadBalancer:
        servers:
          - url: 'http://127.0.0.1:4646'

    adguard:
      loadBalancer:
        servers:
          - url: 'http://100.64.0.2:3000'
