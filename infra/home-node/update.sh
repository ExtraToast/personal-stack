#!/usr/bin/env bash
# update.sh — Pull latest config and re-apply to the home node.
#
# Runs daily via systemd timer. Can also be run manually.
set -euo pipefail

STACK_DIR="${STACK_DIR:-/opt/personal-stack}"

cd "${STACK_DIR}"
git fetch origin main
git reset --hard origin/main

# Re-template configs in case they changed
bash "${STACK_DIR}/infra/home-node/setup.sh" configure

echo "Update complete."
