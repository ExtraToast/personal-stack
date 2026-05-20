"""Best-effort selection between a heavy and light Ollama endpoint.

When ``settings.ollama_heavy_base_url`` is configured and the heavy
endpoint responds to a short probe, the curator runs its chat (i.e.
classification) calls against the heavy model on that endpoint. When
the heavy endpoint is unset or unreachable, the curator falls back to
the existing in-cluster Ollama with its smaller model. Embedding +
reranker calls never route here — they stay on the light endpoint so
``kb_notes.embedding_model`` doesn't churn across passes.

Probe contract:

    GET ${heavy_base_url}/models
        with connect + read timeout = ollama_heavy_probe_timeout_seconds

    Any 2xx response → heavy wins.
    Any non-2xx, transport error, or timeout → light wins.

One probe per resolver call. The curator's CronJob invokes the
resolver once at the start of each 5-min pass, which is the right
cadence: short-lived pod, single decision, no shared cache to manage.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass

import httpx

from curator.settings import Settings

logger = logging.getLogger(__name__)


@dataclass(frozen=True, slots=True)
class ChatEndpoint:
    """Resolved chat endpoint + model pair."""

    base_url: str
    model: str
    profile: str  # "heavy" | "light"


def resolve_chat(settings: Settings, *, client: httpx.Client | None = None) -> ChatEndpoint:
    """Return the chat endpoint to use for this curator pass.

    ``client`` is exposed for tests to inject a respx transport; the
    default opens a one-shot client scoped to the configured probe
    timeout. The light endpoint is read from ``settings.ollama_base_url``
    + ``settings.ollama_chat_model``; the heavy endpoint from the
    matching ``ollama_heavy_*`` fields. A blank heavy URL short-circuits
    to light without doing any network I/O — keeps the resolver inert
    on environments that have not opted in.
    """

    light = ChatEndpoint(
        base_url=settings.ollama_base_url,
        model=settings.ollama_chat_model,
        profile="light",
    )

    heavy_base = settings.ollama_heavy_base_url.strip()
    if not heavy_base:
        logger.info(
            "ollama_router.skip",
            extra={"reason": "heavy_base_url_unset", "endpoint": "light"},
        )
        return light

    timeout = settings.ollama_heavy_probe_timeout_seconds
    probe_url = heavy_base.rstrip("/") + "/models"

    # Use the caller-supplied client (tests) or open a one-shot scoped
    # to the probe timeout. The default httpx client has a 5 s timeout
    # which is too long for "best effort" — a healthy Ollama answers
    # /models in tens of milliseconds.
    owned: httpx.Client | None = None
    try:
        if client is None:
            owned = httpx.Client(timeout=timeout)
            probe_client = owned
        else:
            probe_client = client

        response = probe_client.get(probe_url, timeout=timeout)
        response.raise_for_status()
    except (httpx.TimeoutException, httpx.HTTPError) as exc:
        logger.warning(
            "ollama_router.probe_failed",
            extra={
                "probe_url": probe_url,
                "error": f"{type(exc).__name__}: {exc}",
                "endpoint": "light",
            },
        )
        return light
    finally:
        if owned is not None:
            owned.close()

    heavy = ChatEndpoint(
        base_url=heavy_base,
        model=settings.ollama_heavy_chat_model,
        profile="heavy",
    )
    logger.info(
        "ollama_router.selected",
        extra={
            "endpoint": "heavy",
            "base_url": heavy.base_url,
            "model": heavy.model,
        },
    )
    return heavy
