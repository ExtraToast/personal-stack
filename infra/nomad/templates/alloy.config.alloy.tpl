logging {
  level  = "info"
  format = "logfmt"
}

otelcol.receiver.otlp "default" {
  grpc {
    endpoint = "0.0.0.0:{{ env "NOMAD_PORT_otlp_grpc" }}"
  }

  http {
    endpoint = "0.0.0.0:{{ env "NOMAD_PORT_otlp_http" }}"
  }

  output {
    logs   = [otelcol.processor.memory_limiter.default.input]
    traces = [otelcol.processor.memory_limiter.default.input]
  }
}

otelcol.processor.memory_limiter "default" {
  check_interval = "1s"
  limit          = "128MiB"
  spike_limit    = "32MiB"

  output {
    logs   = [otelcol.processor.batch.default.input]
    traces = [otelcol.processor.batch.default.input]
  }
}

otelcol.processor.batch "default" {
  output {
    logs   = [otelcol.processor.attributes.loki_hints.input]
    traces = [otelcol.exporter.otlphttp.tempo.input]
  }
}

otelcol.processor.attributes "loki_hints" {
  action {
    key    = "loki.resource.labels"
    action = "upsert"
    value  = "service.name, deployment.environment"
  }

  output {
    logs = [otelcol.exporter.loki.otel.input]
  }
}

otelcol.exporter.otlphttp "tempo" {
  client {
    endpoint = "http://127.0.0.1:4318"
    tls {
      insecure             = true
      insecure_skip_verify = true
    }
  }
}

otelcol.exporter.loki "otel" {
  forward_to = [loki.write.default.receiver]
}

discovery.docker "containers" {
  host = "unix:///var/run/docker.sock"
}

discovery.relabel "docker_logs" {
  targets = []

  rule {
    source_labels = ["__meta_docker_container_name"]
    regex         = "/(.*)"
    target_label  = "container"
  }

  rule {
    source_labels = ["__meta_docker_container_label_com_hashicorp_nomad_job_name"]
    regex         = "(.+)"
    target_label  = "service"
  }

  rule {
    source_labels = ["__meta_docker_container_label_com_hashicorp_nomad_job_name"]
    regex         = "(.+)"
    target_label  = "service_name"
  }

  rule {
    source_labels = ["__meta_docker_container_label_com_hashicorp_nomad_task_name"]
    regex         = "(.+)"
    target_label  = "task"
  }
}

loki.source.docker "containers" {
  host          = "unix:///var/run/docker.sock"
  targets       = discovery.docker.containers.targets
  labels        = {}
  relabel_rules = discovery.relabel.docker_logs.rules
  forward_to    = [loki.write.default.receiver]
}

loki.write "default" {
  endpoint {
    url = "http://127.0.0.1:3100/loki/api/v1/push"
  }
}
