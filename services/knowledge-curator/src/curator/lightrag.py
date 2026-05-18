"""LightRAG REST server client.

The curator publishes every promoted note to LightRAG so the graph
+ vector index stays in sync with the canonical corpus. Failure is
non-fatal — LightRAG being down should not block promotion; the
next pass re-publishes any note whose state diverges (LightRAG's
`doc-status` ledger tracks this).
"""

from __future__ import annotations

from dataclasses import dataclass

import httpx
import structlog

log = structlog.get_logger(__name__)


@dataclass(frozen=True, slots=True)
class LightRagDocument:
    """Minimal projection of a promoted note for LightRAG ingestion."""

    id: str
    title: str
    body: str
    scope: str
    type: str


class LightRagClient:
    """POSTs documents to the LightRAG REST server's text-ingest endpoint.

    Designed to be optional: callers that don't want LightRAG flip
    `enabled=False` (or set `LIGHTRAG_BASE_URL=""` in env) and every
    method becomes a no-op. The curator runs through with FTS-only
    recall in that case.
    """

    def __init__(
        self,
        *,
        base_url: str,
        timeout_seconds: float = 30.0,
        enabled: bool = True,
        client: httpx.Client | None = None,
    ) -> None:
        self._base_url = base_url.rstrip("/")
        self._timeout = timeout_seconds
        self._enabled = enabled and bool(base_url)
        self._client = client or httpx.Client(timeout=timeout_seconds)
        self._owns_client = client is None

    def close(self) -> None:
        if self._owns_client:
            self._client.close()

    @property
    def enabled(self) -> bool:
        return self._enabled

    def publish(self, doc: LightRagDocument) -> bool:
        """Ingest the document. Returns True on success, False otherwise.

        The error path is deliberately swallowed — failure here is
        not a reason to skip the promotion's git + DB writes. The
        next curator pass that touches this note re-publishes via
        LightRAG's own doc-status reconciliation.
        """

        if not self._enabled:
            return False
        # LightRAG's text-ingest endpoint accepts {description, text}
        # where description goes into the doc metadata and text is the
        # chunked body. We pre-pend the title + scope + type as
        # context — LightRAG's extraction prompt benefits from
        # human-readable headers above the body.
        prelude = f"# {doc.title}\n\nScope: {doc.scope}\nType: {doc.type}\n\n"
        payload = {
            "text": prelude + doc.body,
            "description": f"{doc.scope} / {doc.type} / {doc.id}",
        }
        try:
            response = self._client.post(
                f"{self._base_url}/documents/text",
                json=payload,
            )
            response.raise_for_status()
        except (httpx.HTTPError, httpx.TimeoutException) as exc:
            log.warning("lightrag.publish_failed", id=doc.id, error=str(exc))
            return False
        return True
