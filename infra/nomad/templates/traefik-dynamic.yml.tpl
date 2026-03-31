http:
  routers:
    vault:
      rule: 'Host(`vault.jorisjonkers.dev`)'
      entryPoints:
        - websecure
      service: vault
      middlewares:
        - rate-limit
        - security-headers
      tls:
        certResolver: cloudflare

    traefik-dashboard:
      rule: 'Host(`traefik.jorisjonkers.dev`)'
      entryPoints:
        - websecure
      service: api@internal
      middlewares:
        - forward-auth
        - rate-limit
        - security-headers
      tls:
        certResolver: cloudflare

  middlewares:
    forward-auth:
      forwardAuth:
        address: 'https://auth.jorisjonkers.dev/api/v1/auth/verify'
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
      headers:
        stsSeconds: 31536000
        stsIncludeSubdomains: true
        stsPreload: true
        frameDeny: true
        contentTypeNosniff: true
        referrerPolicy: 'strict-origin-when-cross-origin'
        contentSecurityPolicy: "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https://*.jorisjonkers.dev https://*.jorisjonkers.test; font-src 'self'; connect-src 'self' https://*.jorisjonkers.dev https://*.jorisjonkers.test; frame-ancestors 'none'"
        customResponseHeaders:
          X-Frame-Options: 'DENY'

    stalwart-security-headers:
      headers:
        stsSeconds: 31536000
        stsIncludeSubdomains: true
        stsPreload: true
        frameDeny: true
        contentTypeNosniff: true
        referrerPolicy: 'strict-origin-when-cross-origin'
        contentSecurityPolicy: "default-src 'self'; script-src 'self' 'unsafe-inline' 'wasm-unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https://*.jorisjonkers.dev https://*.jorisjonkers.test; font-src 'self'; connect-src 'self' https://*.jorisjonkers.dev https://*.jorisjonkers.test; frame-ancestors 'none'"
        customResponseHeaders:
          X-Frame-Options: 'DENY'

    n8n-security-headers:
      headers:
        stsSeconds: 31536000
        stsIncludeSubdomains: true
        stsPreload: true
        frameDeny: true
        contentTypeNosniff: true
        referrerPolicy: 'strict-origin-when-cross-origin'
        contentSecurityPolicy: "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval' https://cdn-rs.n8n.io; style-src 'self' 'unsafe-inline'; img-src 'self' data: https://*.jorisjonkers.dev https://*.jorisjonkers.test; font-src 'self'; connect-src 'self' https://*.jorisjonkers.dev https://*.jorisjonkers.test https://api.n8n.io https://ph.n8n.io; frame-ancestors 'none'"
        customResponseHeaders:
          X-Frame-Options: 'DENY'

    grafana-security-headers:
      headers:
        stsSeconds: 31536000
        stsIncludeSubdomains: true
        stsPreload: true
        frameDeny: true
        contentTypeNosniff: true
        referrerPolicy: 'strict-origin-when-cross-origin'
        contentSecurityPolicy: "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https://*.jorisjonkers.dev https://*.jorisjonkers.test; font-src 'self'; connect-src 'self' https://*.jorisjonkers.dev https://*.jorisjonkers.test; frame-ancestors 'none'"
        customResponseHeaders:
          X-Frame-Options: 'DENY'

  services:
    vault:
      loadBalancer:
        servers:
          - url: 'http://127.0.0.1:8200'
