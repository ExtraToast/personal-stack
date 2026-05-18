from __future__ import annotations

import httpx
import respx

from curator.recall import RecallClient


@respx.mock
def test_recall_unwraps_structured_content() -> None:
    respx.post("http://api/mcp").mock(
        return_value=httpx.Response(
            200,
            json={
                "jsonrpc": "2.0",
                "id": 1,
                "result": {
                    "content": [{"type": "text", "text": "Recall returned 2"}],
                    "structuredContent": {
                        "hits": [
                            {
                                "id": "01HA",
                                "type": "lesson",
                                "scope": "topic:vault",
                                "title": "Hello",
                                "snippet": "world",
                                "score": 0.9,
                            },
                            {
                                "id": "01HB",
                                "type": "lesson",
                                "scope": "personal",
                                "title": "Two",
                                "snippet": "two",
                                "score": 0.6,
                            },
                        ]
                    },
                    "isError": False,
                },
            },
        ),
    )
    client = RecallClient(base_url="http://api", bearer_token="secret")
    hits = client.recall(query="hello", limit=5, scope="topic:vault")
    assert [h.id for h in hits] == ["01HA", "01HB"]
    assert hits[0].score == 0.9


@respx.mock
def test_recall_raises_when_envelope_carries_error() -> None:
    respx.post("http://api/mcp").mock(
        return_value=httpx.Response(
            200,
            json={"jsonrpc": "2.0", "id": 1, "error": {"code": -1, "message": "nope"}},
        ),
    )
    client = RecallClient(base_url="http://api", bearer_token="secret")
    try:
        client.recall(query="hello")
    except RuntimeError as exc:
        assert "recall failed" in str(exc)
    else:
        raise AssertionError("expected RuntimeError")
