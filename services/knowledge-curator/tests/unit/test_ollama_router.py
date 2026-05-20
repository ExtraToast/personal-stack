"""Unit tests for the heavy/light Ollama router.

Covers the three branches that matter operationally:
  1. Heavy URL unset → skip the probe entirely, return light.
  2. Heavy URL set, probe succeeds → return heavy.
  3. Heavy URL set, probe fails (timeout / 5xx / connection refused) →
     return light.
"""

from __future__ import annotations

from dataclasses import replace

import httpx
import pytest
import respx

from curator.ollama_router import ChatEndpoint, resolve_chat
from curator.settings import Settings


def _settings(**overrides: object) -> Settings:
    """Build a `Settings` instance with safe defaults for tests.

    We pull a real `Settings.from_env({})` and override individual
    fields with `dataclasses.replace`, so the test reflects the
    production defaulting logic rather than re-encoding it.
    """

    base = Settings.from_env({})
    return replace(base, **overrides)  # type: ignore[arg-type]


def test_resolve_chat_returns_light_when_heavy_url_unset() -> None:
    settings = _settings(ollama_heavy_base_url="")

    endpoint = resolve_chat(settings)

    assert endpoint.profile == "light"
    assert endpoint.base_url == settings.ollama_base_url
    assert endpoint.model == settings.ollama_chat_model


def test_resolve_chat_returns_heavy_when_probe_succeeds() -> None:
    heavy_url = "http://ollama-heavy.utility-system.svc.cluster.local:11434/v1"
    settings = _settings(
        ollama_heavy_base_url=heavy_url,
        ollama_heavy_chat_model="qwen2.5:14b-instruct",
        ollama_heavy_probe_timeout_seconds=2.0,
    )

    with respx.mock(base_url=heavy_url) as mock_router:
        mock_router.get("/models").respond(
            200,
            json={
                "object": "list",
                "data": [{"id": "qwen2.5:14b-instruct", "object": "model"}],
            },
        )
        endpoint = resolve_chat(settings)

    assert endpoint == ChatEndpoint(
        base_url=heavy_url,
        model="qwen2.5:14b-instruct",
        profile="heavy",
    )


@pytest.mark.parametrize(
    "side_effect",
    [
        httpx.ConnectError("connection refused"),
        httpx.ConnectTimeout("connect timed out"),
        httpx.ReadTimeout("read timed out"),
    ],
)
def test_resolve_chat_falls_back_on_transport_failure(
    side_effect: Exception,
) -> None:
    heavy_url = "http://ollama-heavy.utility-system.svc.cluster.local:11434/v1"
    settings = _settings(
        ollama_heavy_base_url=heavy_url,
        ollama_heavy_chat_model="qwen2.5:14b-instruct",
        ollama_heavy_probe_timeout_seconds=0.1,
    )

    with respx.mock(base_url=heavy_url) as mock_router:
        mock_router.get("/models").mock(side_effect=side_effect)
        endpoint = resolve_chat(settings)

    assert endpoint.profile == "light"
    assert endpoint.base_url == settings.ollama_base_url
    assert endpoint.model == settings.ollama_chat_model


def test_resolve_chat_falls_back_on_http_5xx() -> None:
    heavy_url = "http://ollama-heavy.utility-system.svc.cluster.local:11434/v1"
    settings = _settings(
        ollama_heavy_base_url=heavy_url,
        ollama_heavy_chat_model="qwen2.5:14b-instruct",
        ollama_heavy_probe_timeout_seconds=2.0,
    )

    with respx.mock(base_url=heavy_url) as mock_router:
        mock_router.get("/models").respond(503)
        endpoint = resolve_chat(settings)

    assert endpoint.profile == "light"
