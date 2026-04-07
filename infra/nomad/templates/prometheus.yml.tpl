global:
  scrape_interval: 15s
  evaluation_interval: 15s

# Application and infrastructure metrics are scraped directly by Prometheus.
# Tempo adds trace-derived service graphs and span metrics through remote_write.
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
          - rabbitmq-metrics
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
