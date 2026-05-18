from __future__ import annotations

import httpx
import respx

from curator.lightrag import LightRagClient, LightRagDocument

_DOC = LightRagDocument(
    id="01H",
    title="Vault Agent uid alignment",
    body="some body lines",
    scope="topic:vault",
    type="lesson",
)


@respx.mock
def test_publish_posts_documents_text_with_prelude() -> None:
    route = respx.post("http://lightrag/documents/text").mock(
        return_value=httpx.Response(200, json={"status": "queued"}),
    )
    client = LightRagClient(base_url="http://lightrag")
    assert client.publish(_DOC) is True
    sent = route.calls.last.request.read().decode()
    assert "Vault Agent uid alignment" in sent
    assert "Scope: topic:vault" in sent
    assert "some body lines" in sent


@respx.mock
def test_publish_swallows_http_errors_and_returns_false() -> None:
    respx.post("http://lightrag/documents/text").mock(
        return_value=httpx.Response(503, text="overloaded"),
    )
    client = LightRagClient(base_url="http://lightrag")
    assert client.publish(_DOC) is False


def test_publish_short_circuits_when_disabled() -> None:
    client = LightRagClient(base_url="", enabled=False)
    # No network call should happen — respx isn't even mocking
    # anything here. The method must return False without raising.
    assert client.publish(_DOC) is False
    assert client.enabled is False
