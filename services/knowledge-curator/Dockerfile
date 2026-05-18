# syntax=docker/dockerfile:1.7

FROM python:3.12-slim-bookworm AS base
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1
# git + openssh-client are needed for `git mv` + push on the shared
# vault clone (same reason the worker image carries them).
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        git \
        openssh-client \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY --from=ghcr.io/astral-sh/uv:0.4.30 /uv /usr/local/bin/uv

# -- build stage -----------------------------------------------------
FROM base AS build
WORKDIR /app
COPY services/knowledge-curator/pyproject.toml /app/pyproject.toml
COPY services/knowledge-curator/uv.lock /app/uv.lock
COPY services/knowledge-curator/src /app/src
ENV UV_PROJECT_ENVIRONMENT=/app/.venv
RUN uv sync --frozen --no-dev --no-cache --python 3.12

# -- runtime ---------------------------------------------------------
FROM base
ARG GIT_SHA=unknown
ENV SERVICE_VERSION=${GIT_SHA} \
    PATH=/app/.venv/bin:$PATH \
    PYTHONPATH=/app/src
WORKDIR /app
COPY --from=build /app/.venv /app/.venv
COPY services/knowledge-curator/src /app/src

# Same uid as the worker so the shared vault-clone PVC's files stay
# owned by 1000:1000 across both writers.
RUN groupadd --system --gid 1000 curator \
    && useradd --system --uid 1000 --gid 1000 --home /home/curator curator \
    && mkdir -p /home/curator && chown -R curator:curator /home/curator /app
USER curator

ENTRYPOINT ["opentelemetry-instrument", "python", "-m", "curator"]
