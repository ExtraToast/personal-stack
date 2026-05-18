"""knowledge-api recall client.

Hits the MCP `/mcp` endpoint with a `tools/call` for `recall`. We
talk to knowledge-api rather than reaching into Postgres directly
so the curator stays decoupled from the database schema and shares
the same FTS+ranking behaviour as Claude Code's MCP recall.

Only the structured projection on the response is used here, so the
content envelope MCP returns (PR #337) wraps a `RecallHit` array
behind `result.structuredContent.hits`. We pull straight from there.
"""

from __future__ import annotations

from dataclasses import dataclass

import httpx


@dataclass(frozen=True, slots=True)
class RecallHit:
    id: str
    type: str
    scope: str
    title: str
    snippet: str
    score: float


class RecallClient:
    """Stateless HTTP client for the knowledge-api MCP recall tool."""

    def __init__(
        self,
        *,
        base_url: str,
        bearer_token: str,
        timeout_seconds: float = 15.0,
        client: httpx.Client | None = None,
    ) -> None:
        self._base_url = base_url.rstrip("/")
        self._bearer_token = bearer_token
        self._timeout = timeout_seconds
        self._client = client or httpx.Client(timeout=timeout_seconds)
        self._owns_client = client is None

    def close(self) -> None:
        if self._owns_client:
            self._client.close()

    def recall(self, *, query: str, limit: int = 5, scope: str | None = None) -> list[RecallHit]:
        arguments: dict[str, object] = {"query": query, "limit": limit}
        if scope is not None:
            arguments["scope"] = scope
        payload = {
            "jsonrpc": "2.0",
            "id": 1,
            "method": "tools/call",
            "params": {"name": "knowledge.recall", "arguments": arguments},
        }
        response = self._client.post(
            f"{self._base_url}/mcp",
            json=payload,
            headers={"Authorization": f"Bearer {self._bearer_token}"},
        )
        response.raise_for_status()
        envelope = response.json()
        if "error" in envelope:
            raise RuntimeError(f"recall failed: {envelope['error']}")
        structured = envelope.get("result", {}).get("structuredContent", {})
        return [
            RecallHit(
                id=str(hit["id"]),
                type=str(hit.get("type", "")),
                scope=str(hit.get("scope", "")),
                title=str(hit.get("title", "")),
                snippet=str(hit.get("snippet", "")),
                score=float(hit.get("score", 0.0)),
            )
            for hit in structured.get("hits", [])
        ]
