"""Ollama embedding HTTP client.

Wraps `/v1/embeddings` for the OpenAI-compatible path. The curator
uses embeddings to pull top-K nearest existing notes when
classifying — so the LLM sees concrete neighbours rather than
having to remember the whole vault.

Single-call shape: one input string → one 768-dim float vector.
Batching is available upstream but we stay single-shot to keep the
caller simple; one inbox file at a time.
"""

from __future__ import annotations

from dataclasses import dataclass

import httpx


@dataclass(frozen=True, slots=True)
class Embedding:
    vector: tuple[float, ...]
    model: str


class OllamaEmbedder:
    """Stateless embedding client.

    Shares an ``httpx.Client`` with the classifier when one is injected
    so a single connection pool covers both call patterns.
    """

    def __init__(
        self,
        *,
        base_url: str,
        model: str,
        timeout_seconds: float = 60.0,
        client: httpx.Client | None = None,
    ) -> None:
        self._base_url = base_url.rstrip("/")
        self._model = model
        self._timeout = timeout_seconds
        self._client = client or httpx.Client(timeout=timeout_seconds)
        self._owns_client = client is None

    def close(self) -> None:
        if self._owns_client:
            self._client.close()

    def embed(self, text: str) -> Embedding:
        response = self._client.post(
            f"{self._base_url}/embeddings",
            json={"model": self._model, "input": text},
        )
        response.raise_for_status()
        data = response.json()
        try:
            vector = tuple(float(x) for x in data["data"][0]["embedding"])
        except (KeyError, IndexError, TypeError, ValueError) as exc:
            raise RuntimeError(f"Unexpected /embeddings response shape: {data!r}") from exc
        return Embedding(vector=vector, model=self._model)
