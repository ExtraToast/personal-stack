# stalwart-tools: bakes the pinned stalwart-cli plus the reconcile
# scripts, plan templates and account manifest. Used by the production
# apply sidecar (platform stalwart Deployment) and the dev compose
# stalwart-bootstrap service. Build context is the repo root so CI's
# build matrix (which always builds from `.`) and docker-compose share
# one Dockerfile.
FROM alpine:3.21

ARG STALWART_CLI_VERSION=1.0.7

RUN apk add --no-cache curl ca-certificates jq gettext tar xz \
 && arch="$(uname -m)" \
 && case "$arch" in \
      x86_64)  target=x86_64-unknown-linux-musl ;; \
      aarch64) target=aarch64-unknown-linux-musl ;; \
      *) echo "unsupported arch: $arch" >&2; exit 1 ;; \
    esac \
 && curl -fsSL "https://github.com/stalwartlabs/cli/releases/download/v${STALWART_CLI_VERSION}/stalwart-cli-${target}.tar.xz" -o /tmp/cli.tar.xz \
 && tar -xJf /tmp/cli.tar.xz -C /tmp \
 && find /tmp -type f -name stalwart-cli -exec mv {} /usr/local/bin/stalwart-cli \; \
 && chmod +x /usr/local/bin/stalwart-cli \
 && rm -rf /tmp/*

COPY infra/stalwart/apply.sh infra/stalwart/bootstrap.sh /opt/stalwart-tools/
COPY infra/stalwart/plan.ndjson.tmpl infra/stalwart/plan.dev.ndjson infra/stalwart/accounts.json /opt/stalwart-tools/
RUN chmod +x /opt/stalwart-tools/apply.sh /opt/stalwart-tools/bootstrap.sh

# No default CMD: the compose service and the k8s sidecar each set their
# own command (bootstrap.sh for dev, apply.sh for production).
