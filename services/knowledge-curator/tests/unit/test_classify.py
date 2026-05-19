from __future__ import annotations

import httpx
import pytest
import respx

from curator.classify import (
    Classification,
    ClassificationError,
    Neighbour,
    OllamaClassifier,
    response_format,
    system_prompt,
    user_prompt,
)


def test_response_format_carries_strict_json_schema() -> None:
    fmt = response_format()
    assert fmt["type"] == "json_schema"
    assert fmt["json_schema"]["strict"] is True
    schema = fmt["json_schema"]["schema"]
    assert schema["required"] == [
        "title",
        "scope",
        "type",
        "tags",
        "supersedes",
        "see_also",
        "confidence",
    ]
    assert schema["properties"]["type"]["enum"] == ["lesson", "decision", "note", "fact"]


def test_system_prompt_lists_closed_topic_vocabulary() -> None:
    out = system_prompt(("kotlin", "python"))
    assert "kotlin" in out
    assert "python" in out
    # The schema is embedded in the prompt as belt-and-braces.
    assert "topic:" in out


def test_user_prompt_wraps_neighbours_in_xml() -> None:
    out = user_prompt(
        title="t",
        body="b",
        neighbours=[
            Neighbour(id="01H", title="n1", scope="topic:vault", snippet="hello"),
        ],
        inbox_scope_hint="_inbox",
    )
    assert "<candidate>" in out and "</candidate>" in out
    assert "<inbox-scope-hint>_inbox</inbox-scope-hint>" in out
    assert '<neighbour id="01H" scope="topic:vault">' in out


def _classification_payload(**overrides: object) -> dict[str, object]:
    base = {
        "title": "Vault Agent uid alignment",
        "scope": "topic:vault",
        "topic": "vault",
        "type": "lesson",
        "tags": ["vault", "kubernetes"],
        "supersedes": [],
        "see_also": [],
        "confidence": 0.8,
        "needs_review_reason": None,
    }
    base.update(overrides)
    return base


def _chat_response(content: str) -> dict[str, object]:
    return {
        "choices": [{"message": {"role": "assistant", "content": content}}],
    }


@respx.mock
def test_classify_returns_classification_on_valid_json() -> None:
    import json

    route = respx.post("http://ollama/v1/chat/completions").mock(
        return_value=httpx.Response(
            200, json=_chat_response(json.dumps(_classification_payload()))
        ),
    )
    classifier = OllamaClassifier(
        base_url="http://ollama/v1", model="qwen2.5:14b", topic_slugs=("vault",)
    )
    result = classifier.classify(title="t", body="b", neighbours=[])
    assert isinstance(result, Classification)
    assert result.scope == "topic:vault"
    assert route.call_count == 1


@respx.mock
def test_classify_retries_once_on_validation_failure() -> None:
    import json

    bad = json.dumps(
        {
            "title": "x",
            "scope": "nope",
            "type": "lesson",
            "tags": [],
            "supersedes": [],
            "see_also": [],
            "confidence": 0.5,
        }
    )
    good = json.dumps(_classification_payload())
    respx.post("http://ollama/v1/chat/completions").mock(
        side_effect=[
            httpx.Response(200, json=_chat_response(bad)),
            httpx.Response(200, json=_chat_response(good)),
        ],
    )
    classifier = OllamaClassifier(
        base_url="http://ollama/v1", model="qwen2.5:14b", topic_slugs=("vault",)
    )
    result = classifier.classify(title="t", body="b", neighbours=[])
    assert result.scope == "topic:vault"


@respx.mock
def test_classify_raises_after_two_invalid_responses() -> None:
    import json

    bad = json.dumps({"nope": True})
    respx.post("http://ollama/v1/chat/completions").mock(
        return_value=httpx.Response(200, json=_chat_response(bad)),
    )
    classifier = OllamaClassifier(
        base_url="http://ollama/v1", model="qwen2.5:14b", topic_slugs=("vault",)
    )
    with pytest.raises(ClassificationError):
        classifier.classify(title="t", body="b", neighbours=[])


@respx.mock
def test_classify_raises_classification_error_on_timeout() -> None:
    # A slow Ollama (180s timeout in prod) used to bubble httpx.TimeoutException
    # through promote_inbox_file and crash the whole curator pass — leaving
    # the rest of the inbox untouched. classify() now wraps transport errors
    # in ClassificationError so promote.py's existing handler routes the note
    # to needs-review and the loop keeps walking.
    respx.post("http://ollama/v1/chat/completions").mock(
        side_effect=httpx.ReadTimeout("simulated slow ollama"),
    )
    classifier = OllamaClassifier(
        base_url="http://ollama/v1", model="qwen2.5:14b", topic_slugs=("vault",)
    )
    with pytest.raises(ClassificationError, match="Ollama transport error"):
        classifier.classify(title="t", body="b", neighbours=[])


@respx.mock
def test_classify_raises_classification_error_on_5xx() -> None:
    respx.post("http://ollama/v1/chat/completions").mock(
        return_value=httpx.Response(503, text="upstream"),
    )
    classifier = OllamaClassifier(
        base_url="http://ollama/v1", model="qwen2.5:14b", topic_slugs=("vault",)
    )
    with pytest.raises(ClassificationError, match="Ollama transport error"):
        classifier.classify(title="t", body="b", neighbours=[])


@respx.mock
def test_classify_raises_classification_error_on_connect_failure() -> None:
    respx.post("http://ollama/v1/chat/completions").mock(
        side_effect=httpx.ConnectError("connection refused"),
    )
    classifier = OllamaClassifier(
        base_url="http://ollama/v1", model="qwen2.5:14b", topic_slugs=("vault",)
    )
    with pytest.raises(ClassificationError, match="Ollama transport error"):
        classifier.classify(title="t", body="b", neighbours=[])
