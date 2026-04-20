#!/usr/bin/env bash
# Wait for the main system-test routes to become reachable.

set -Eeuo pipefail

check_route() {
  local route="$1" url="$2"
  echo -n "  ${route} (${url}): "
  for _ in $(seq 1 60); do
    if curl -sfk --max-time 2 -o /dev/null "${url}" 2>/dev/null; then
      echo "reachable"
      return 0
    fi
    sleep 2
  done
  echo "TIMEOUT"
  return 1
}

# Stalwart downloads its web UI zip asynchronously on first boot. Until
# that finishes the root path 200s but the body is a JSON 404
# (`"status":404,"title":"not found"`), which makes MainSiteServiceLaunchTest
# flakily fail asserting page content contains "stalwart management".
# Poll until the response no longer looks like that bootstrap-phase 404.
check_stalwart_webui() {
  local url="https://stalwart.jorisjonkers.test/"
  echo -n "  stalwart-webui (${url}): "
  for _ in $(seq 1 90); do
    local body
    body=$(curl -sk --max-time 2 "${url}" 2>/dev/null || true)
    if [ -n "${body}" ] && ! echo "${body}" | grep -q '"status":404'; then
      echo "reachable"
      return 0
    fi
    sleep 2
  done
  echo "TIMEOUT"
  return 1
}

echo "Waiting for Traefik routes to become reachable..."
check_route auth-api      "https://auth.jorisjonkers.test/api/actuator/health/liveness"
check_route assistant-api "https://assistant.jorisjonkers.test/api/actuator/health/liveness"
check_route app-ui        "https://jorisjonkers.test/"
check_route auth-ui       "https://auth.jorisjonkers.test/"
check_route assistant-ui  "https://assistant.jorisjonkers.test/"
check_route vault         "https://vault.jorisjonkers.test/ui/"
check_stalwart_webui
