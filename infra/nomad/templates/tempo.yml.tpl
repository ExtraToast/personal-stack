server:
  http_listen_port: 3200
  grpc_listen_port: 9096

memberlist:
  bind_port: 7946

ingester:
  max_block_duration: 30s

distributor:
  receivers:
    otlp:
      protocols:
        grpc:
          endpoint: 0.0.0.0:4317
        http:
          endpoint: 0.0.0.0:4318

# Keep Tempo metrics-generator enabled for service graphs and span metrics.
# Application and infrastructure metrics stay in Prometheus' scrape path.
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
