# ADR-018: Monitoring & Observability

## Status

Accepted

## Date

2026-03-25

## Context

A self-hosted stack needs observability to detect issues, debug problems, and track performance. With multiple services
on a single VPS, resource efficiency matters.

## Decision

### Grafana Stack (unified observability)

#### Logging

- **Loki + Promtail + Grafana**
- Promtail collects logs from Docker containers (via Docker log driver or bind-mount /var/log)
- Loki stores and indexes logs
- Grafana provides LogQL query interface
- Structured JSON logging from all services (Spring Boot + Vue error tracking)

#### Metrics

- **Prometheus + Grafana**
- Spring Boot Actuator exposes /actuator/prometheus endpoint
- Prometheus scrapes services exposed by the Nomad deployment
- Traefik exposes Prometheus metrics natively
- Node Exporter for host-level metrics (CPU, memory, disk, network)
- Grafana dashboards for: service health, JVM metrics, HTTP latency, error rates

#### Tracing

- **OpenTelemetry + Tempo**
- OpenTelemetry Java agent for automatic instrumentation of Spring Boot services
- Trace propagation via W3C Trace Context headers
- Tempo stores traces, Grafana provides trace visualization
- Correlate traces with logs via trace ID

### Uptime Monitoring

- **Uptime Kuma** (self-hosted) at status.jorisjonkers.dev
- HTTP/HTTPS checks for all public subdomains
- TCP check for SSH (port 2222)
- Certificate expiry monitoring
- Status page (optional public)

### Alerting

- **Email + Discord**
- Grafana alerting rules for: service down, high error rate, high latency, disk usage, cert expiry
- Uptime Kuma notifications for: endpoint down, SSL expiry
- n8n can be used for custom alert enrichment/routing

### Resource Budget

Estimated memory for observability stack:

- Prometheus: ~200MB
- Loki: ~200MB
- Promtail: ~50MB
- Tempo: ~200MB
- Grafana: ~150MB
- Uptime Kuma: ~100MB
- **Total: ~900MB** — fits within the 12GB VPS allocation

## Consequences

- Full Grafana stack provides unified dashboards for logs, metrics, and traces
- OpenTelemetry is vendor-neutral — can switch backends later
- ~900MB overhead is significant on 12GB — monitor and tune retention policies
- Log retention should be limited (7-14 days) to manage disk usage
- Prometheus retention similar (15 days default)
- Tempo retention: shorter (3-7 days) as traces are most useful for recent debugging
