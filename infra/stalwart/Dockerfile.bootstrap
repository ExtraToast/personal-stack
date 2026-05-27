# Ships the pinned stalwart-cli and the declarative plan, then applies
# the plan against the stalwart container once it is healthy. The same
# image pattern backs the production apply Job.
FROM alpine:3.21

ARG STALWART_CLI_VERSION=1.0.7

RUN apk add --no-cache curl ca-certificates tar xz \
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

COPY plan.dev.ndjson /plan/plan.ndjson
COPY bootstrap.sh /usr/local/bin/bootstrap.sh
RUN chmod +x /usr/local/bin/bootstrap.sh

ENTRYPOINT ["/usr/local/bin/bootstrap.sh"]
