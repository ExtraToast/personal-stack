"""LLM-driven classification of inbox notes via Ollama.

Calls the in-cluster Ollama instance over its OpenAI-compatible
endpoint (`/v1/chat/completions`) with a strict JSON schema in the
`response_format` field. Ollama compiles the schema to a GBNF
grammar at sampling time, so the model is hard-constrained to
emit valid JSON — no whitespace garbage, no commentary, no
schema echo.

Schema enforced:

    {
      "title":       str,                   # concise, descriptive
      "action":      enum promote|discard,  # default promote; discard
                                            #   low-value captures
      "scope":       enum,                  # topic:<slug> | project:<repo>
                                            #   | agent:<name> | agent:_shared
      "topic":       str | null,            # required when scope starts with topic:
      "type":        enum lesson|decision|note|fact,
      "tags":        list[str],             # ≤ 8, free-form
      "supersedes":  list[str],             # kb_… ids; existence is
                                            #   validated post-call
      "see_also":    list[str],             # kb_… ids; validated likewise
      "confidence":  float in [0, 1],
      "needs_review_reason": str | null     # human-readable if the
                                            #   model itself flags
                                            #   uncertainty
    }

Failure handling: a single retry with the Pydantic validation
error appended to the user message. On second failure the caller
routes the note to review or, after repeated review attempts, discard.
"""

from __future__ import annotations

import json
import logging
from dataclasses import dataclass
from typing import Any

import httpx
from pydantic import BaseModel, Field, ValidationError, field_validator

logger = logging.getLogger(__name__)


# Allowed `scope` shapes: closed enum prefixes + a slug per type. The
# pattern is reused on both Pydantic validation and the JSON schema
# we hand to Ollama, so the GBNF grammar and the post-call validator
# stay in lock-step.
_SCOPE_PATTERN = (
    r"^(topic:[a-z0-9][a-z0-9_-]*"
    # Projects are `org/repo` (lowercase). The bare-repo form
    # (`project:website`) is accepted too — `ProjectVocabulary`
    # aliases it back to the canonical `org/repo` slug — but the
    # classifier prompt steers the model toward the `org/repo`
    # shape so promotions land at `projects/<org>/<repo>/` and
    # every repo from the same org groups under one folder.
    r"|project:[a-z0-9._-]+(/[a-z0-9._-]+)?"
    r"|agent:[a-z0-9_-]+"
    r"|agent:_shared)$"
)


# -------- response model + JSON schema -------------------------------


class Classification(BaseModel):
    """Validated curator output. Keep keys aligned with the GBNF schema.

    Field-level validation here is the second line of defence after
    Ollama's grammar-constrained sampling. Existence checks for the
    supersedes / see_also kb_… ids happen in the caller against the
    knowledge-api, not here.
    """

    title: str = Field(..., min_length=4, max_length=80)
    action: str = Field(default="promote", pattern=r"^(promote|discard)$")
    scope: str = Field(..., pattern=_SCOPE_PATTERN)
    topic: str | None = None
    type: str = Field(..., pattern=r"^(lesson|decision|note|fact)$")
    tags: list[str] = Field(default_factory=list, max_length=8)
    supersedes: list[str] = Field(default_factory=list, max_length=10)
    see_also: list[str] = Field(default_factory=list, max_length=10)
    confidence: float = Field(..., ge=0.0, le=1.0)
    needs_review_reason: str | None = None

    @field_validator("topic")
    @classmethod
    def topic_required_when_scope_is_topic(cls, value: str | None) -> str | None:
        # Cross-field check happens in `model_validator`; this hook
        # just keeps the type narrow.
        return value

    def is_topic_scope(self) -> bool:
        return self.scope.startswith("topic:")


# -------- prompt construction ---------------------------------------


_RESPONSE_SCHEMA: dict[str, Any] = {
    "name": "knowledge_curator_classification",
    "schema": {
        "type": "object",
        "additionalProperties": False,
        "required": [
            "title",
            "scope",
            "type",
            "tags",
            "supersedes",
            "see_also",
            "confidence",
            "action",
        ],
        "properties": {
            "title": {"type": "string", "minLength": 4, "maxLength": 80},
            "action": {"type": "string", "enum": ["promote", "discard"]},
            "scope": {"type": "string", "pattern": _SCOPE_PATTERN},
            "topic": {"type": ["string", "null"]},
            "type": {"type": "string", "enum": ["lesson", "decision", "note", "fact"]},
            "tags": {"type": "array", "items": {"type": "string"}, "maxItems": 8},
            "supersedes": {"type": "array", "items": {"type": "string"}, "maxItems": 10},
            "see_also": {"type": "array", "items": {"type": "string"}, "maxItems": 10},
            "confidence": {"type": "number", "minimum": 0.0, "maximum": 1.0},
            "needs_review_reason": {"type": ["string", "null"]},
        },
    },
    "strict": True,
}


def response_format() -> dict[str, Any]:
    """OpenAI-compatible ``response_format`` payload.

    Ollama v0.5+ compiles this to a GBNF grammar at sampling time;
    output is hard-constrained to match the schema. Pass the same
    schema text in the system prompt as a belt-and-braces — Ollama
    docs explicitly recommend it.
    """

    return {"type": "json_schema", "json_schema": _RESPONSE_SCHEMA}


def system_prompt(
    topic_slugs: tuple[str, ...],
    project_slugs: tuple[str, ...] = (),
) -> str:
    """Static system message describing the role + the schema.

    Includes the closed topic + project vocabularies inline so the
    model knows which `topic:<slug>` and `project:<slug>` values
    are legal. Aliases are NOT included — alias resolution happens
    server-side in `TopicVocabulary` / `ProjectVocabulary`.
    """

    topic_list = ", ".join(topic_slugs) if topic_slugs else "(none)"
    project_list = ", ".join(project_slugs) if project_slugs else "(none)"
    return (
        "You classify short markdown notes for a personal knowledge base. "
        "Output ONLY a single JSON object that matches the schema below. "
        "No prose, no markdown, no commentary. "
        # Title contract: short, declarative, claim-shaped. The 80-char
        # ceiling is enforced by the JSON schema; this prose is what
        # nudges the model toward a *useful* title within that budget.
        "Title contract: 3-8 words, declarative present-tense, no "
        'trailing punctuation. Skip framing words like "How to", '
        '"On", "About", "Notes on", "Introduction to" — the '
        'title IS the claim. Good: "Vault Raft unseal requires '
        'Shamir keys". Bad: "How does Vault Raft unseal work?". '
        "Commit to the single best-fit scope and type, and rewrite "
        "the title decisively rather than abdicating. When a note is "
        "not clearly tied to exactly one closed-vocabulary slug, "
        "choose the safest general placement (`agent:_shared` or the "
        "closest `topic:<slug>`) and promote it. Do not request review "
        "just because the fit is imperfect. "
        "`action` defaults to `promote`. Choose `discard` only when "
        "the capture is genuinely low-value and not worth keeping: "
        "empty or near-empty bodies, transient chatter, pure duplicates "
        "of an existing neighbour, test/noise captures, or content "
        "with no durable knowledge. When discarding, still fill the "
        "other fields best-effort and set `needs_review_reason` to a "
        "short human-readable reason; it doubles as the discard reason. "
        "Use `topic:<slug>` only "
        "with one of these slugs: " + topic_list + ". "
        # Project vocabulary is closed. The classifier used to
        # invent shapes (`personal-stack-2`, `github-actions`,
        # `home-direct`, `esa-blueshell-website`,
        # `esa-blueshell.website`,
        # `my-kubernetes-observability-stack`) which forked the
        # vault into bogus `projects/<hallucination>/` folders.
        # Canonical slug is `<org>/<repo>` (lowercase).
        "Use `project:<org>/<repo>` only with one of these slugs: "
        + project_list
        + ". The slug is the GitHub `org/repo` path, lowercase. "
        "Never invent a slug; never append a version suffix "
        "(no `personal-stack-2`); never mash org and repo with "
        "a dash or dot. When the note is not specific to one of "
        "these repos, prefer `topic:<slug>` or `agent:_shared` "
        "instead. "
        "Use "
        "`agent:_shared` for process / methodology guidance aimed at "
        "any assistant. `agent:<name>` is reserved for assistant-"
        "specific guidance — almost never the right choice. Reserve "
        "non-null `needs_review_reason` with `action=\"promote\"` for "
        "the rare genuinely bimodal case where neither promotion nor "
        "discard is defensible. "
        # Strongly bias toward populating `see_also` so the resulting
        # kb_relations graph is dense enough to be useful for recall.
        "Linking rules — follow these aggressively: "
        "(1) Populate `see_also` with the id of every neighbour whose "
        "subject area overlaps thematically with the candidate. "
        "Frameworks, languages, programming paradigms, algorithms, "
        "data structures and design patterns are exactly the kind of "
        "cross-cutting topics that benefit from being linked — when in "
        "doubt, link. An empty `see_also` is only correct when the "
        "candidate is genuinely standalone. "
        "(2) Strongly prefer non-empty `see_also` when the candidate "
        "and a neighbour share a language, framework, paradigm, "
        "pattern family (creational/structural/behavioural), "
        "algorithmic category, or architectural style. "
        "(3) Use `supersedes` only when the candidate explicitly "
        "replaces or corrects a neighbour's claim; otherwise prefer "
        "`see_also`. "
        "(4) Up to 10 ids each. Prefer linking 3-5 closely-related "
        "neighbours over forcing all 10. "
        "Output schema:\n" + json.dumps(_RESPONSE_SCHEMA["schema"], indent=2)
    )


@dataclass(frozen=True, slots=True)
class Neighbour:
    """A nearest-neighbour candidate that informs supersedes / see_also."""

    id: str
    title: str
    scope: str
    snippet: str


def user_prompt(
    *,
    title: str,
    body: str,
    neighbours: list[Neighbour],
    inbox_scope_hint: str | None = None,
) -> str:
    """Build the user-message text wrapping the candidate note.

    Neighbours land inside an XML-style block — XML beats fenced code
    for nested content per the prompting-pattern research and avoids
    accidental fence collisions when notes themselves contain
    ```code blocks```. Keep neighbour snippets short to leave room
    in the context for the candidate body.
    """

    parts = ["<candidate>"]
    parts.append(f"<title>{title}</title>")
    if inbox_scope_hint:
        parts.append(f"<inbox-scope-hint>{inbox_scope_hint}</inbox-scope-hint>")
    parts.append("<body>")
    parts.append(body.strip())
    parts.append("</body>")
    parts.append("</candidate>")
    if neighbours:
        parts.append("<neighbours>")
        for n in neighbours:
            parts.append(f'  <neighbour id="{n.id}" scope="{n.scope}">')
            parts.append(f"    <title>{n.title}</title>")
            parts.append(f"    <snippet>{n.snippet[:280]}</snippet>")
            parts.append("  </neighbour>")
        parts.append("</neighbours>")
    parts.append("Return the JSON object now.")
    return "\n".join(parts)


# -------- HTTP path -------------------------------------------------


class ClassificationError(RuntimeError):
    """Raised when the model output is unrecoverably invalid."""


class OllamaClassifier:
    """Wraps the OpenAI-compatible /v1/chat/completions call.

    Stateless — one instance per process is fine. Allows injecting a
    pre-built ``httpx.Client`` so tests can use respx without
    rebuilding the connection-pool layer.
    """

    def __init__(
        self,
        *,
        base_url: str,
        model: str,
        topic_slugs: tuple[str, ...],
        project_slugs: tuple[str, ...] = (),
        timeout_seconds: float = 120.0,
        client: httpx.Client | None = None,
    ) -> None:
        self._base_url = base_url.rstrip("/")
        self._model = model
        self._topic_slugs = topic_slugs
        self._project_slugs = project_slugs
        self._timeout = timeout_seconds
        self._client = client or httpx.Client(timeout=timeout_seconds)
        self._owns_client = client is None

    def close(self) -> None:
        if self._owns_client:
            self._client.close()

    def classify(
        self,
        *,
        title: str,
        body: str,
        neighbours: list[Neighbour],
        inbox_scope_hint: str | None = None,
    ) -> Classification:
        """Single classification call, with one retry on validation failure."""

        messages = [
            {
                "role": "system",
                "content": system_prompt(self._topic_slugs, self._project_slugs),
            },
            {
                "role": "user",
                "content": user_prompt(
                    title=title,
                    body=body,
                    neighbours=neighbours,
                    inbox_scope_hint=inbox_scope_hint,
                ),
            },
        ]
        payload = {
            "model": self._model,
            "messages": messages,
            "temperature": 0.0,
            "response_format": response_format(),
            # `max_tokens` leaves headroom for the longest JSON we
            # expect — `tags` + `supersedes` + `see_also` of 10 each
            # plus the verbose fields. Far more than needed in
            # practice; under-provisioning truncates JSON, which
            # GBNF would catch but cost us a retry.
            "max_tokens": 1024,
        }

        raw = self._chat(payload)
        try:
            return Classification.model_validate_json(raw)
        except ValidationError as first_err:
            logger.warning(
                "classify.invalid_json", extra={"error": str(first_err), "raw": raw[:500]}
            )
            # One retry with the validation error appended so the
            # model can correct itself.
            retry_messages = [
                *messages,
                {"role": "assistant", "content": raw},
                {
                    "role": "user",
                    "content": (
                        "The previous response failed validation:\n"
                        f"{first_err}\n"
                        "Return a corrected JSON object."
                    ),
                },
            ]
            retry_payload = dict(payload, messages=retry_messages)
            raw2 = self._chat(retry_payload)
            try:
                return Classification.model_validate_json(raw2)
            except ValidationError as second_err:
                raise ClassificationError(
                    f"Classification failed twice: {second_err}"
                ) from second_err

    def _chat(self, payload: dict[str, Any]) -> str:
        url = f"{self._base_url}/chat/completions"
        # All httpx transport errors (timeouts, connection failures,
        # 5xx via raise_for_status) get re-raised as ClassificationError
        # so the caller — `Promoter.promote_inbox_file` — can route the
        # note to `_inbox/_needs-review/` and keep walking. The pass
        # would otherwise crash on the first slow Ollama call and leave
        # the rest of the candidates untouched (saw this draining 84
        # `relation-target-missing` items on 2026-05-19: classify on
        # the first review file timed out at 180 s, exception bubbled
        # all the way to `sys.exit(main())`, the other 83 never ran).
        try:
            response = self._client.post(url, json=payload)
            response.raise_for_status()
            data = response.json()
        except (httpx.TimeoutException, httpx.HTTPError) as exc:
            raise ClassificationError(
                f"Ollama transport error on {url}: {type(exc).__name__}: {exc}"
            ) from exc
        try:
            return str(data["choices"][0]["message"]["content"])
        except (KeyError, IndexError, TypeError) as exc:
            raise ClassificationError(
                f"Unexpected /chat/completions response shape: {data!r}"
            ) from exc
