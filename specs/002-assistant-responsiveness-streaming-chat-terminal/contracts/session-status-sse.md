# Contract: Session list/status SSE (Phase 3, P3)

## Endpoint

`GET /api/v1/sessions/events` (SSE) — replaces client polling of session lists +
`GET .../turns` for status.

- **Auth**: cookie session + `X-User-Id`, scoped to the caller's sessions only.
- **Response**: `text/event-stream`, `Cache-Control: no-cache`,
  `X-Accel-Buffering: no`. Via `SseEmitter`.
- **Consumption**: `fetch()` + `ReadableStream` (CSRF/cookies; same reason as the
  chat stream — no `EventSource`). On open the server may emit an initial
  `session` event per existing session (snapshot), then deltas.

## Events

```
event: session            # upsert: created or status/idle changed
data: {"sessionId":"<uuid>","status":"STARTING|RUNNING|STOPPED","idle":false,"ts":"<iso>"}

event: removed            # session ended/archived
data: {"sessionId":"<uuid>"}

: keepalive               # SSE comment, every < proxy idle timeout (ref: 30s)
```

## Server behavior

- A status producer fans out changes to subscribed emitters. Sources: session
  lifecycle transitions (STARTING→RUNNING→STOPPED) and idle state from
  `WorkspaceActivityTracker`/`ConnectedClientTracker` (reuse, do not duplicate).
- Per-subscriber emitter; register/deregister on connect/close; bounded set.
- Send periodic keepalive comments so proxies hold the stream open (FR-009).

## Client behavior

- Subscribe once per app instance; update the Pinia session list/status from
  events. Remove the existing poll loop. Reconnect with backoff on stream close
  (mirror the terminal socket's reconnect posture).

## Acceptance mapping

- FR-008/SC-005: remote changes reflected within a few seconds, no manual refresh.
- FR-009: idle stream stays open via keepalive.
- Scaling: one stream per view with keepalive instead of N polls/min per view.
