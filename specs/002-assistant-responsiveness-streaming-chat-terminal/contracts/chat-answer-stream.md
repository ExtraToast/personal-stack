# Contract: Chat answer stream (Phase 1, P1)

## Endpoint

`POST /api/v1/chat-sessions/{id}/messages:stream`

- **Auth**: same as existing chat endpoints — cookie session + `X-XSRF-TOKEN`
  header + `X-User-Id` (as other `ChatSessionController` routes).
- **Request body**: `{ "body": "<user prompt>" }` (the user message text).
- **Response**: `Content-Type: text/event-stream`, `Cache-Control: no-cache`,
  `X-Accel-Buffering: no`. Returned via `SseEmitter`.

## Server behavior

1. Persist the **user** message immediately (reuse `AppendChatMessageCommand`,
   role `USER`) so it survives regardless of answer outcome.
2. Open the SSE stream. Build retrieval context via the existing
   `RetrievalPort`/`ContextBuilder`, then stream the answer from LightRAG
   `/query/stream`.
3. Emit events as they arrive; accumulate full answer text server-side.
4. On success: persist one assistant `ChatMessage` (role `ASSISTANT`) with the
   full text, then emit `done` carrying its id; complete the emitter.
5. On error/timeout: emit `error`; persist **nothing**; `completeWithError`.

## Events

```
event: chunk
data: {"text":"partial answer text"}

event: done
data: {"messageId":"<uuid>","createdAt":"<iso>"}

event: error
data: {"message":"<short reason>","retryable":true}
```

- `chunk` repeats 0..N times. Client appends `text` to the in-progress assistant
  message in order received.
- Exactly one terminal event (`done` or `error`) is sent.

## Client behavior (assistant-ui)

- `chatSessionsService.ts#streamAnswer(id, body)`: `fetch` POST with credentials +
  `X-XSRF-TOKEN`; read `response.body` via a `ReadableStream` reader; parse SSE
  frames; yield `{type:'chunk'|'done'|'error', ...}`.
- Store creates a placeholder assistant message (`pending`/`streaming`), appends
  each `chunk`, marks `complete` on `done` (swap in the persisted id), or `failed`
  on `error`/network abort with a retry affordance.

## Acceptance mapping

- FR-001/SC-001: first `chunk` visible well before `done`.
- FR-002/SC-002: persisted assistant message equals the concatenation of all
  `chunk.text`.
- FR-003: `error` → no assistant row persisted, message shown failed + retryable.

## Fallback

If `rag.enabled=false` or `/query/stream` is unavailable: call non-streaming
`/query`, emit the whole answer as one `chunk`, then `done`. Contract unchanged.
