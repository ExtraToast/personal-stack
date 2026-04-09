# Observability

## Default Architecture

The stack uses one collector tier and three signal-specific backends:

- Traces: applications and Traefik export OTLP to Alloy, Alloy exports traces to Tempo.
- Logs: services log to stdout/stderr, Alloy collects logs and sends them to Loki.
- Metrics: Prometheus scrapes application and infrastructure metrics directly.

Grafana is query-only. It is not in the ingestion path.

## Signal Ownership

Use this model consistently:

- `OTLP` is the transport and collector ingress protocol.
- `Tempo` stores traces.
- `Loki` stores logs.
- `Prometheus` stores metrics.

Do not treat OTLP as meaning "send every signal directly to every backend".

## Metrics Policy

Application metrics stay pull-based by default:

- Spring Boot metrics come from Actuator and Micrometer Prometheus endpoints.
- Infrastructure metrics come from the existing Prometheus scrape jobs and exporters.
- Tempo `metrics_generator` stays enabled because the existing Grafana trace dashboards use service graphs and span metrics derived from traces.

That means the repo has two metrics sources on purpose:

- Prometheus scrape for application and infrastructure metrics
- Tempo trace-derived metrics for service graphs and span metrics

## Platform Defaults

### Kotlin / Spring Boot

- Use the OpenTelemetry Java agent for tracing.
- Export traces to Alloy over OTLP.
- Keep `OTEL_METRICS_EXPORTER=none` unless there is a specific reason to move a service to OTLP metrics later.
- Emit structured JSON logs with `traceId`, `spanId`, `service.name`, `service.version`, and `deployment.environment`.
- Expose metrics through `/api/actuator/prometheus`.

### Python

- Use OpenTelemetry tracing.
- Export traces to Alloy over OTLP.
- Prefer structured stdout/stderr logs with trace and span correlation over direct OTLP log export by default.
- Prefer Prometheus metrics by default.

### Vue Browser

- Start with tracing only.
- Use conservative sampling.
- Do not default to browser OTLP logs.
- Do not default to browser OTLP metrics.

## Environment Model

### Docker Compose

- Grafana queries backend services by Docker DNS name.
- Applications export traces to `alloy:4318`.
- Tempo runs as `grafana/tempo:2.10.3`.

### Nomad

- Grafana uses host-mode-safe datasource wiring.
- Applications export traces to the local Alloy endpoint once the Nomad Alloy job is in place.
- Tempo runs as `grafana/tempo:2.10.3`.

## Tempo Version

The repo standard is `grafana/tempo:2.10.3`.

This version is intentionally pinned as part of the observability lightweighting work because newer Tempo releases materially reduced memory usage compared with the older `2.7.2` baseline previously used here.
