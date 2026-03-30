#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CERT_DIR="$ROOT_DIR/infra/traefik/dynamic-dev/certs"
CERT_FILE="$CERT_DIR/jorisjonkers.test.crt"
KEY_FILE="$CERT_DIR/jorisjonkers.test.key"

mkdir -p "$CERT_DIR"

if [[ -f "$CERT_FILE" && -f "$KEY_FILE" ]]; then
  echo "TLS: dev wildcard certificate already present."
  exit 0
fi

openssl req \
  -x509 \
  -nodes \
  -newkey rsa:2048 \
  -sha256 \
  -days 3650 \
  -subj "/CN=jorisjonkers.test" \
  -addext "subjectAltName=DNS:jorisjonkers.test,DNS:*.jorisjonkers.test" \
  -keyout "$KEY_FILE" \
  -out "$CERT_FILE"

echo "TLS: generated dev wildcard certificate for *.jorisjonkers.test."
