#!/usr/bin/env bash
# Validate that all Nomad jobs become healthy. No tests executed.
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
# shellcheck source=.github/scripts/nomad-ci-lib.sh
source "${SCRIPT_DIR}/nomad-ci-lib.sh"

DOMAIN="jorisjonkers.test"
IMAGE_REPO="personal-stack"
IMAGE_TAG="ci"

ensure_vault_unsealed

# Phase 1: Data tier (submit all, then wait)
echo "==> Phase 1: Deploying data tier (postgres, valkey, rabbitmq)"
submit_nomad_job "${ROOT_DIR}/infra/nomad/jobs/data/postgres.nomad.hcl" -var "repo_dir=${ROOT_DIR}"
submit_nomad_job "${ROOT_DIR}/infra/nomad/jobs/data/valkey.nomad.hcl"
submit_nomad_job "${ROOT_DIR}/infra/nomad/jobs/data/rabbitmq.nomad.hcl" \
  -var "domain=${DOMAIN}" -var "repo_dir=${ROOT_DIR}" -var "oidc_tls_skip_verify=true"
wait_for_nomad_jobs postgres 240 valkey 180 rabbitmq 180

# Phase 2: Edge
echo "==> Phase 2: Deploying edge (traefik)"
submit_nomad_job "${ROOT_DIR}/infra/nomad/jobs/edge/traefik.nomad.hcl" \
  -var "domain=${DOMAIN}" \
  -var "tls_mode=file" \
  -var "tls_cert_dir=/srv/nomad/traefik/certs"
wait_for_nomad_jobs traefik 180

# Phase 3: All apps in parallel
echo "==> Phase 3: Deploying apps (auth-api, assistant-api, auth-ui, assistant-ui, app-ui)"
for svc in auth-api assistant-api auth-ui assistant-ui app-ui; do
  submit_nomad_job "${ROOT_DIR}/infra/nomad/jobs/apps/${svc}.nomad.hcl" \
    -var "domain=${DOMAIN}" \
    -var "image_tag=${IMAGE_TAG}" \
    -var "image_repo=${IMAGE_REPO}" \
    -var "count=1"
done
wait_for_nomad_jobs auth-api 300 assistant-api 300 auth-ui 240 assistant-ui 240 app-ui 240

# Phase 4: Platform services in parallel
echo "==> Phase 4: Deploying platform (grafana, n8n, stalwart)"
ensure_vault_unsealed
submit_nomad_job "${ROOT_DIR}/infra/nomad/jobs/observability/grafana.nomad.hcl" \
  -var "domain=${DOMAIN}" -var "repo_dir=${ROOT_DIR}" -var "oidc_tls_skip_verify=true"
submit_nomad_job "${ROOT_DIR}/infra/nomad/jobs/platform/n8n.nomad.hcl" \
  -var "domain=${DOMAIN}" -var "repo_dir=${ROOT_DIR}" \
  -var "oidc_ca_cert_path=/srv/nomad/traefik/certs/wildcard.crt"
submit_nomad_job "${ROOT_DIR}/infra/nomad/jobs/mail/stalwart.nomad.hcl" \
  -var "domain=${DOMAIN}"
wait_for_nomad_jobs grafana 300 n8n 300 stalwart 300

echo "==> All Nomad jobs healthy"
