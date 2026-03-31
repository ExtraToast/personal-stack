# Datasource Authentication Architecture

This document describes how authentication works between Grafana, its datasources (Prometheus, Loki, Tempo), and the
broader observability stack.

## Current Authentication State

| Connection                   | Auth Method   | Notes                                            |
| ---------------------------- | ------------- | ------------------------------------------------ |
| External users -> Grafana    | OAuth2 (OIDC) | Via auth-api with PKCE, auto-login, role mapping |
| Grafana -> Prometheus        | None          | Internal Docker overlay network                  |
| Grafana -> Loki              | None          | Internal Docker overlay network                  |
| Grafana -> Tempo             | None          | Internal Docker overlay network                  |
| Prometheus -> scrape targets | None          | Internal Docker overlay network                  |
| Promtail -> Loki             | None          | Internal push to `loki:3100`                     |
| Services -> Tempo (OTLP)     | None          | Internal push to `tempo:4318`                    |

### Prometheus (`prometheus:9090`)

No authentication configured. Prometheus scrapes targets using plain HTTP on internal service names. The Prometheus API
is not exposed outside the Docker overlay network.

### Loki (`loki:3100`)

Authentication is explicitly disabled (`auth_enabled: false` in `loki.yaml`). Loki operates in single-tenant mode.
Promtail pushes logs to `http://loki:3100/loki/api/v1/push` without credentials.

### Tempo (`tempo:3200`)

No authentication configured. OTLP receivers listen on `0.0.0.0:4317` (gRPC) and `0.0.0.0:4318` (HTTP) without auth.
Services send traces directly to these endpoints.

### Prometheus Scrape Targets

Prometheus scrapes the following internal endpoints without authentication:

- `auth-api:8081/api/actuator/prometheus`
- `assistant-api:8082/api/actuator/prometheus`
- `traefik:8080` (built-in metrics)
- `grafana:3000` (built-in metrics)
- `loki:3100` (built-in metrics)
- `tempo:3200` (built-in metrics)
- `rabbitmq:15692` (rabbitmq_prometheus plugin)

## Why This Is Acceptable

1. **Network isolation**: All observability services run on the `personal-stack-overlay` Docker Swarm overlay network.
   This network is not accessible from outside the Swarm cluster.

2. **No external exposure**: Prometheus, Loki, and Tempo have **no Traefik routes** configured in `routers.yml`. They
   cannot be reached from the internet.

3. **Single entry point**: The only externally-accessible observability endpoint is Grafana at
   `grafana.jorisjonkers.dev`, which is protected by OAuth2 authentication via auth-api.

4. **Standard pattern**: This is the recommended deployment pattern for self-hosted Grafana stacks where all components
   run on the same trusted network.

## Data Flow

```
External Users
    |
    v
[Traefik] ── TLS termination, security headers, rate limiting
    |
    v  (OAuth2 via auth-api required)
[Grafana :3000]
    |── http://prometheus:9090  (internal, no auth)
    |── http://loki:3100        (internal, no auth)
    └── http://tempo:3200       (internal, no auth)

[Promtail] ──> http://loki:3100/loki/api/v1/push     (internal)
[Prometheus] ──> auth-api:8081/api/actuator/prometheus (internal)
             ──> assistant-api:8082/api/actuator/prometheus
             ──> traefik:8080, grafana:3000, loki:3100, tempo:3200
             ──> rabbitmq:15692
[auth-api]     ──> http://tempo:4318 (OTLP traces, internal)
[assistant-api] ──> http://tempo:4318 (OTLP traces, internal)
```

## Where OAuth2 IS the Security Boundary

| Service             | Auth Method         | Details                                                                                              |
| ------------------- | ------------------- | ---------------------------------------------------------------------------------------------------- |
| Grafana             | OAuth2 (OIDC)       | `grafana` client in auth-api, PKCE, role mapping: `ROLE_ADMIN` -> `GrafanaAdmin`, others -> `Viewer` |
| RabbitMQ Management | OAuth2 (OIDC)       | `rabbitmq` client in auth-api, scope: `rabbitmq.tag:administrator`                                   |
| Vault UI            | OIDC + forward-auth | `vault` client in auth-api, bound claims require `ROLE_ADMIN` or `SERVICE_VAULT`                     |
| Other services      | Forward-auth        | Traefik middleware validates session via `auth-api:8081/api/v1/auth/verify`                          |

## Options for Future Hardening

If stricter internal authentication is ever needed (e.g., multi-tenant deployment, zero-trust networking):

### Prometheus

Add `--web.config.file=/etc/prometheus/web.yml` with basic_auth or TLS:

```yaml
basic_auth_users:
  grafana: <bcrypt-hash>
tls_server_config:
  cert_file: /etc/prometheus/tls/cert.pem
  key_file: /etc/prometheus/tls/key.pem
```

### Loki

Enable multi-tenant mode (`auth_enabled: true`). All requests must include an `X-Scope-OrgID` header. Configure Grafana
datasource with a custom header.

### Tempo

Front with a reverse proxy (nginx/envoy sidecar) that enforces basic auth or mTLS. Tempo itself has no built-in
authentication.

### mTLS via Vault PKI

The stack already has a Vault PKI engine configured. Internal services could use short-lived certificates for mutual
TLS:

1. Issue certs from Vault PKI for each service
2. Configure TLS on Prometheus, Loki, Tempo
3. Configure Grafana datasources with client certificates
4. Auto-rotate via Vault agent or sidecar

This is the most robust option but adds operational complexity. Recommended only if the threat model requires
defense-in-depth within the Docker network.
