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

echo "Waiting for Traefik routes to become reachable..."
check_route auth-api      "https://auth.jorisjonkers.test/api/actuator/health/liveness"
check_route assistant-api "https://assistant.jorisjonkers.test/api/actuator/health/liveness"
check_route app-ui        "https://jorisjonkers.test/"
check_route auth-ui       "https://auth.jorisjonkers.test/"
check_route assistant-ui  "https://assistant.jorisjonkers.test/"
check_route vault         "https://vault.jorisjonkers.test/ui/"
