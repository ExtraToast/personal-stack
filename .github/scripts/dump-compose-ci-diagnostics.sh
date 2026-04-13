#!/usr/bin/env bash
# Dump Docker Compose diagnostics on CI failure.
set -euo pipefail

COMPOSE_FILES="-f docker-compose.yml -f docker-compose.ci.yml"

echo "==> Date"
date -u

echo "==> System memory"
free -h

echo "==> Docker Compose service status"
docker compose ${COMPOSE_FILES} ps -a

echo "==> Docker Compose logs (last 200 lines per service)"
docker compose ${COMPOSE_FILES} logs --tail=200

echo "==> Docker disk usage"
docker system df
