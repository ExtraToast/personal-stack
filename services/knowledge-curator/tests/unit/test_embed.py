from __future__ import annotations

import httpx
import pytest
import respx

from curator.embed import OllamaEmbedder


@respx.mock
def test_embed_returns_vector_for_valid_response() -> None:
    respx.post("http://ollama/v1/embeddings").mock(
        return_value=httpx.Response(
            200,
            json={
                "data": [{"embedding": [0.1, 0.2, 0.3]}],
                "model": "nomic-embed-text",
            },
        ),
    )
    embedder = OllamaEmbedder(base_url="http://ollama/v1", model="nomic-embed-text")
    out = embedder.embed("hello")
    assert out.vector == (0.1, 0.2, 0.3)
    assert out.model == "nomic-embed-text"


@respx.mock
def test_embed_raises_runtime_error_on_unexpected_shape() -> None:
    respx.post("http://ollama/v1/embeddings").mock(
        return_value=httpx.Response(200, json={"unexpected": True}),
    )
    embedder = OllamaEmbedder(base_url="http://ollama/v1", model="nomic-embed-text")
    with pytest.raises(RuntimeError, match="Unexpected /embeddings"):
        embedder.embed("hello")


def test_embedder_close_is_safe_when_client_was_injected() -> None:
    with httpx.Client() as client:
        e = OllamaEmbedder(base_url="http://ollama/v1", model="m", client=client)
        # close() must not close the injected client; verify by
        # reusing the same client object afterwards.
        e.close()
        assert not client.is_closed
