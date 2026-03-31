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
    http:
      tls:
        certResolver: cloudflare
  traefik:
    address: ':8080'

certificatesResolvers:
  cloudflare:
    acme:
      email: admin@jorisjonkers.dev
      storage: /letsencrypt/acme.json
      dnsChallenge:
        provider: cloudflare
        resolvers:
          - '1.1.1.1:53'
          - '1.0.0.1:53'

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
*** Add File: /Users/j.w.jonkers/IDEAProjects/private-stack/infra/nomad/templates/traefik.env.tpl
CF_DNS_API_TOKEN={{ with secret "secret/data/platform/edge" }}{{ index .Data.data "cloudflare.dns_api_token" }}{{ end }}
*** Add File: /Users/j.w.jonkers/IDEAProjects/private-stack/infra/nomad/templates/traefik-dynamic.yml.tpl
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
*** Add File: /Users/j.w.jonkers/IDEAProjects/private-stack/infra/nomad/templates/postgres.env.tpl
POSTGRES_DB=postgres
POSTGRES_USER={{ with secret "secret/data/platform/postgres" }}{{ index .Data.data "postgres.user" }}{{ end }}
POSTGRES_PASSWORD={{ with secret "secret/data/platform/postgres" }}{{ index .Data.data "postgres.password" }}{{ end }}
AUTH_DB_PASSWORD={{ with secret "secret/data/platform/postgres" }}{{ index .Data.data "auth.password" }}{{ end }}
ASSISTANT_DB_PASSWORD={{ with secret "secret/data/platform/postgres" }}{{ index .Data.data "assistant.password" }}{{ end }}
N8N_DB_PASSWORD={{ with secret "secret/data/platform/automation" }}{{ index .Data.data "n8n.db_password" }}{{ end }}
*** Add File: /Users/j.w.jonkers/IDEAProjects/private-stack/infra/nomad/templates/rabbitmq.env.tpl
RABBITMQ_DEFAULT_USER={{ with secret "secret/data/platform/rabbitmq" }}{{ index .Data.data "rabbitmq.user" }}{{ end }}
RABBITMQ_DEFAULT_PASS={{ with secret "secret/data/platform/rabbitmq" }}{{ index .Data.data "rabbitmq.password" }}{{ end }}
*** Add File: /Users/j.w.jonkers/IDEAProjects/private-stack/infra/nomad/templates/n8n.env.tpl
DB_POSTGRESDB_USER={{ with secret "secret/data/platform/automation" }}{{ index .Data.data "n8n.db_user" }}{{ end }}
DB_POSTGRESDB_PASSWORD={{ with secret "secret/data/platform/automation" }}{{ index .Data.data "n8n.db_password" }}{{ end }}
OIDC_CLIENT_SECRET={{ with secret "secret/data/platform/automation" }}{{ index .Data.data "n8n.oauth_client_secret" }}{{ end }}
*** Add File: /Users/j.w.jonkers/IDEAProjects/private-stack/infra/nomad/templates/grafana.env.tpl
GF_SECURITY_ADMIN_USER={{ with secret "secret/data/platform/observability" }}{{ index .Data.data "grafana.admin_user" }}{{ end }}
GF_SECURITY_ADMIN_PASSWORD={{ with secret "secret/data/platform/observability" }}{{ index .Data.data "grafana.admin_password" }}{{ end }}
GF_AUTH_GENERIC_OAUTH_CLIENT_SECRET={{ with secret "secret/data/platform/observability" }}{{ index .Data.data "grafana.oauth_client_secret" }}{{ end }}
*** Add File: /Users/j.w.jonkers/IDEAProjects/private-stack/infra/nomad/templates/prometheus.yml.tpl
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: prometheus
    static_configs:
      - targets: ['127.0.0.1:9090']

  - job_name: consul
    consul_sd_configs:
      - server: '127.0.0.1:8500'
        services:
          - auth-api
          - assistant-api
          - traefik
          - grafana
          - loki
          - tempo
          - rabbitmq
          - stalwart
    relabel_configs:
      - source_labels: [__meta_consul_service]
        target_label: job
      - source_labels: [__meta_consul_service]
        regex: '(auth-api|assistant-api)'
        target_label: __metrics_path__
        replacement: '/api/actuator/prometheus'
      - source_labels: [__meta_consul_service]
        regex: 'stalwart'
        target_label: __metrics_path__
        replacement: '/metrics/prometheus'
*** Add File: /Users/j.w.jonkers/IDEAProjects/private-stack/infra/nomad/templates/promtail.yml.tpl
server:
  http_listen_port: 9080

positions:
  filename: /tmp/positions.yaml

clients:
  - url: http://127.0.0.1:3100/loki/api/v1/push

scrape_configs:
  - job_name: docker
    docker_sd_configs:
      - host: unix:///var/run/docker.sock
        refresh_interval: 5s
    relabel_configs:
      - source_labels: [__meta_docker_container_name]
        regex: '/?(.+)'
        target_label: container
      - source_labels: [__meta_docker_container_label_com_hashicorp_nomad_job_name]
        target_label: service
      - source_labels: [__meta_docker_container_label_com_hashicorp_nomad_task_name]
        target_label: task
*** Add File: /Users/j.w.jonkers/IDEAProjects/private-stack/infra/nomad/templates/tempo.yml.tpl
server:
  http_listen_port: 3200

distributor:
  receivers:
    otlp:
      protocols:
        grpc:
          endpoint: 0.0.0.0:4317
        http:
          endpoint: 0.0.0.0:4318

metrics_generator:
  processor:
    service_graphs:
      dimensions:
        - service.name
    span_metrics:
      dimensions:
        - service.name
        - http.method
        - http.route
  storage:
    path: /var/tempo/generator/wal
    remote_write:
      - url: http://127.0.0.1:9090/api/v1/write

overrides:
  defaults:
    metrics_generator:
      processors:
        - service-graphs
        - span-metrics

storage:
  trace:
    backend: local
    local:
      path: /var/tempo/traces
    wal:
      path: /var/tempo/wal
*** Add File: /Users/j.w.jonkers/IDEAProjects/private-stack/infra/nomad/templates/stalwart-config.toml.tpl
# Stalwart Mail Server Configuration (Nomad)

server.hostname = "mail.jorisjonkers.dev"

server.listener.smtp.bind = "[::]:25"
server.listener.smtp.protocol = "smtp"

server.listener.submission.bind = "[::]:587"
server.listener.submission.protocol = "smtp"
server.listener.submission.tls.starttls = true

server.listener.submissions.bind = "[::]:465"
server.listener.submissions.protocol = "smtp"
server.listener.submissions.tls.implicit = true

server.listener.imap.bind = "[::]:143"
server.listener.imap.protocol = "imap"

server.listener.imaptls.bind = "[::]:993"
server.listener.imaptls.protocol = "imap"
server.listener.imaptls.tls.implicit = true

server.listener.pop3.bind = "[::]:110"
server.listener.pop3.protocol = "pop3"

server.listener.pop3s.bind = "[::]:995"
server.listener.pop3s.protocol = "pop3"
server.listener.pop3s.tls.implicit = true

server.listener.sieve.bind = "[::]:4190"
server.listener.sieve.protocol = "managesieve"

server.listener.https.bind = "[::]:443"
server.listener.https.protocol = "http"
server.listener.https.tls.implicit = true

server.listener.http.bind = "[::]:8080"
server.listener.http.protocol = "http"

acme."letsencrypt".directory = "https://acme-v02.api.letsencrypt.org/directory"
acme."letsencrypt".challenge = "dns-01"
acme."letsencrypt".contact = ["postmaster@jorisjonkers.dev"]
acme."letsencrypt".domains = ["mail.jorisjonkers.dev"]
acme."letsencrypt".cache = "/opt/stalwart/etc/acme"
acme."letsencrypt".renew-before = "30d"
acme."letsencrypt".default = true
acme."letsencrypt".dns.provider = "cloudflare"
acme."letsencrypt".dns.secret = "{{ with secret "secret/data/platform/edge" }}{{ index .Data.data "cloudflare.dns_api_token" }}{{ end }}"

storage.data = "rocksdb"
storage.fts = "rocksdb"
storage.blob = "rocksdb"
storage.lookup = "rocksdb"
storage.directory = "auth-api"

store.rocksdb.type = "rocksdb"
store.rocksdb.path = "/opt/stalwart/data"
store.rocksdb.compression = "lz4"

directory.auth-api.type = "oidc"
directory.auth-api.endpoint.url = "https://auth.jorisjonkers.dev/api/userinfo"
directory.auth-api.endpoint.method = "userinfo"
directory.auth-api.timeout = "15s"
directory.auth-api.fields.email = "email"
directory.auth-api.fields.username = "preferred_username"
directory.auth-api.fields.full-name = "name"
directory.auth-api.cache.size = 1048576
directory.auth-api.cache.ttl.positive = "1h"
directory.auth-api.cache.ttl.negative = "10m"

directory.internal.type = "internal"
directory.internal.store = "rocksdb"

authentication.fallback-admin.user = "{{ with secret "secret/data/platform/mail" }}{{ index .Data.data "stalwart.admin_user" }}{{ end }}"
authentication.fallback-admin.secret = "{{ with secret "secret/data/platform/mail" }}{{ index .Data.data "stalwart.admin_password" }}{{ end }}"

metrics.prometheus.enable = true

tracer.log.type = "log"
tracer.log.level = "info"
tracer.log.path = "/opt/stalwart/logs"
tracer.log.prefix = "stalwart.log"
tracer.log.rotate = "daily"
tracer.log.ansi = false
tracer.log.enable = true
