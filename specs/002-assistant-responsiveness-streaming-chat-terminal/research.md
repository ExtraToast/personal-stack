# Research: Assistant Responsiveness

All decisions are grounded in the current source and verified upstream behavior.

## R1 — Chat answers are not generated server-side today

**Finding**: `ChatSessionController.appendMessage` only dispatches
`AppendChatMessageCommand`, and `AppendChatMessageCommandHandler` only persists a
`ChatMessage`. `ChatSessionKind` documents `PLAIN` as "messages are persisted and
nothing else happens server-side" and `KNOWLEDGE` as reserving the answer "Pod
binding + streaming" for a follow-up. The UI store `chatSessions.ts#send` appends
only the returned (user) message — there is no assistant reply anywhere.

**Decision**: Phase 1 builds the reserved answer path streaming-first. PLAIN keeps
its persist-only behavior (no regression); the streaming endpoint generates an
answer for `KNOWLEDGE` sessions (and is the default for new sessions created to
"ask the assistant"). The spec's wording "instead of buffering the full answer"
is satisfied by never having a buffered phase, not by retrofitting one.

## R2 — Answer source: LightRAG `/query/stream`

**Finding**: `LightRagClient` already calls LightRAG `POST /query` (mix mode),
returning a single fused `{response}` string via sync `RestClient`. Upstream
LightRAG also exposes `POST /query/stream`, which yields NDJSON chunks and sets
`X-Accel-Buffering: no` (and recommends gzip off) for real-time delivery.
Source: HKUDS/LightRAG API server docs.

**Decision**: Add a streaming retrieval call to `LightRagClient` that POSTs to
`/query/stream` and exposes incremental chunks, consumed via
`RestClient.exchange { _, res -> res.body.bufferedReader().lineSequence() }`.
The application service forwards each chunk to the SSE emitter and accumulates the
full text for persistence. **Fallback** (spec Assumption): if `rag.enabled=false`
or streaming errors immediately, fall back to the existing non-streaming `/query`
and emit the whole answer as one `chunk` then `done` — still correct, just not
progressive.

## R3 — SSE on a servlet (Spring MVC) stack

**Finding**: `assistant-api` uses `spring-boot-starter-web` (servlet), not
WebFlux, so reactive `Flux<ServerSentEvent>` is not the idiomatic tool.

**Decision**: Return
`org.springframework.web.servlet.mvc.method.annotation.SseEmitter` from the
controller. Produce chunks on a bounded task executor (there is an existing
`AsyncConfig` with `@EnableAsync`; add/confirm a dedicated, bounded executor for
streaming so model latency cannot exhaust the common pool). Set
`Content-Type: text/event-stream`, `Cache-Control: no-cache`, and
`X-Accel-Buffering: no`. Complete/`completeWithError` the emitter to release the
request thread; register `onTimeout`/`onCompletion` to persist-or-mark-failed
deterministically.

## R4 — Proxy buffering for SSE

**Finding**: SSE breaks if an intermediary buffers `text/event-stream`. The
assistant host is already exposed (the terminal WS works through it), so transport
reaches the pod; the risk is response buffering, not routing.

**Decision**: Rely on `X-Accel-Buffering: no` + `Cache-Control: no-cache` from the
app. Verify end-to-end through Traefik + forward-auth during Phase 1 (quickstart
step). Only if buffering is observed do we touch the deploy manifest (a
middleware/annotation change under `platform/cluster/flux/apps/...`), which would
be a separate, flagged change — not part of the fleet render.

## R5 — Browser SSE consumption with CSRF

**Finding**: UI calls go through `@extratoast/vue-web-commons` `useApiWithAuth`,
which sets `credentials: include` and an `X-XSRF-TOKEN` header from the
`XSRF-TOKEN` cookie. Native `EventSource` cannot set custom headers and is
GET-only, so it cannot carry the CSRF header or a POST body.

**Decision**: Consume the stream with `fetch()` + `response.body.getReader()` and a
small `TextDecoder` + event-frame parser in `chatSessionsService.ts#streamAnswer`.
POST carries the prompt + `X-XSRF-TOKEN`. The store appends each chunk to the
in-progress assistant message; on `done` it marks complete; on `error`/network
drop it sets a `failed` flag and exposes retry.

## R6 — The gateway already implements offset-based tailing (the reference design)

**Finding**: `agent-gateway` is the reference web-terminal's backend:
`AgentSessionManager` runs one tmux session per agent; tmux `pipe-pane` writes raw
PTY output to a per-session append-only **log file** with a disk cap (front-
truncated); `LogTailer` polls that file by byte **offset** (`AtomicLong`), streams
only new bytes, carries partial UTF-8 across reads, and already handles front-
truncation by resetting to 0. On attach, `AgentAttachHandler` sends one
`captureWithEscapes` snapshot then tails from EOF.

**Implication**: Output is written to the log **regardless of whether a browser is
attached** (tmux pipe-pane is independent of WS clients), so bytes produced during
a disconnect gap are already on disk. The missing capability is purely: let a
reconnecting client say "resume from offset O for epoch E" so the tailer starts at
O instead of EOF and the snapshot is skipped.

**Decision**: Implement offset/epoch **in the gateway**, not assistant-api. This is
faithful to the reference design and avoids the harder assistant-api alternative
(its upstream gateway WS is currently 1:1 with the browser WS and torn down on
disconnect, so it cannot buffer the gap). assistant-api only needs to pass the
`epoch`/`offset` query params through to the upstream URI.

## R7 — Epoch source

**Finding**: A stale offset is dangerous only across a _session restart_ (new tmux
session / recreated log), where byte O of the old log is unrelated to the new one.
`LogTailer` already self-heals an in-place front-truncation (length < offset →
reset to 0) but cannot distinguish "new session" from "same session."

**Decision**: Assign an `epoch` per tmux session at creation in
`AgentSessionManager`/`AgentSession` (monotonic counter or creation-time value
stored with the session). On attach: if client `epoch` matches and client `offset`
is within `[logStart, logLength]`, resume the tailer at `offset` and skip the
snapshot; otherwise send the snapshot, tail from EOF, and tell the client the
current `epoch` so it adopts it (client clears the screen). Offset older than the
retained log (below current `logStart` after front-truncation) → treated as
mismatch → snapshot.

## R8 — Reporting offset to the client without breaking the relay

**Finding**: The output envelope is `{"output": "..."}` and assistant-api relays
frames verbatim (`handleTextMessage` → `bridge.upstream.sendMessage(message)`),
with a comment noting a richer Block protocol may parse frames later.

**Decision**: Extend the gateway's outbound frame with an optional running byte
`off` (log offset after this chunk) and send a one-time control frame
`{"epoch": E, "snapshot": true|false}` at attach. Additive JSON fields keep the
relay verbatim and old clients ignore unknown keys. The client stores the latest
`off` and `epoch`; on reconnect it appends `?epoch=E&offset=O` to the attach URL
(through assistant-api). The gateway is the only component that computes byte
offsets — the client never counts UTF-8 bytes itself.

## R9 — Chat list virtualization

**Finding**: `assistant-ui` already depends on PrimeVue 4, which ships
`VirtualScroller`. The current chat list renders a plain `v-for` with no stable
`:key` and no virtualization.

**Decision**: Phase 4 uses PrimeVue `VirtualScroller` with a stable message-id
`:key`. No new dependency. Streaming updates (Phase 1) mutate only the in-progress
message object so the rest of the list is not re-rendered.

## Open tuning values (resolved at implementation, per spec Assumptions)

- Retained log disk cap / window size (gateway already has a cap; confirm it covers
  tens-of-seconds gaps at typical output rates).
- SSE executor pool size and emitter timeout.
- Status-SSE keepalive comment interval (target < proxy idle timeout; the WS
  heartbeat uses 30s as a working reference).

## Sources

- [LightRAG API Server docs (HKUDS/LightRAG)](https://github.com/HKUDS/LightRAG/blob/main/docs/LightRAG-API-Server.md)
- [LightRAG API Server (DeepWiki)](https://deepwiki.com/HKUDS/LightRAG/4-api-server)
