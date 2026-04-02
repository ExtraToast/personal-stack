#!/usr/bin/env bash

for svc in \
  "Vault:host.docker.internal:8200/v1/sys/health" \
  "Postgres:host.docker.internal:5432" \
  "RabbitMQ:host.docker.internal:5672" \
  "Valkey:host.docker.internal:6379" \
  "Tempo:host.docker.internal:4318/v1/status/buildinfo"; do
  name="${svc%%:*}"
  addr="${svc#*:}"
  if [[ "$addr" == */* ]]; then
    result=$(docker run --rm --add-host=host.docker.internal:host-gateway curlimages/curl -sf "http://${addr}" 2>&1) && echo
"$name: OK" || echo "$name: UNREACHABLE"
  else
    host="${addr%:*}"; port="${addr#*:}"
    docker run --rm --add-host=host.docker.internal:host-gateway busybox nc -zw2 "$host" "$port" 2>&1 && echo "$name: OK" ||
echo "$name: UNREACHABLE"
  fi
done
