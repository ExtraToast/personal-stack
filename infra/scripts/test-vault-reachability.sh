#!/usr/bin/env bash
# Test internal service reachability from inside a bridge-mode Docker container,
# using the same host.docker.internal:host-gateway mapping that Nomad app jobs use.

EXTRA_HOST="--add-host=host.docker.internal:host-gateway"
HOST="host.docker.internal"

test_http() {
  local name="$1" url="$2"
  if docker run --rm ${EXTRA_HOST} curlimages/curl -sf "${url}" >/dev/null 2>&1; then
    echo "${name}: OK"
  else
    echo "${name}: UNREACHABLE"
  fi
}

test_tcp() {
  local name="$1" host="$2" port="$3"
  if docker run --rm ${EXTRA_HOST} busybox nc -zw2 "${host}" "${port}" 2>/dev/null; then
    echo "${name}: OK"
  else
    echo "${name}: UNREACHABLE"
  fi
}

test_http  "Vault"    "http://${HOST}:8200/v1/sys/health"
test_tcp   "Postgres" "${HOST}" 5432
test_tcp   "RabbitMQ" "${HOST}" 5672
test_tcp   "Valkey"   "${HOST}" 6379
test_http  "Tempo"    "http://${HOST}:4318/v1/status/buildinfo"
