# Quickstart: Assistant Responsiveness — build, test, verify

One stacked PR per phase. P1 ships first and stands alone.

## Phase 1 — Streaming chat (assistant-api + assistant-ui)

Build & test:

```bash
./gradlew :services:assistant-api:test           # service + SSE controller test (Testcontainers)
(cd services/assistant-ui && npm run typecheck && npm run lint && npm run test)
```

Manual verify (through the real edge):

1. Open a KNOWLEDGE chat session, send a prompt that yields a long answer.
2. Confirm partial text appears in well under ~1s and grows progressively (not all
   at once). Watch the network panel: one `text/event-stream` response, chunked.
3. Reload the session → the persisted assistant message equals what was streamed.
4. Kill the network mid-stream → message shows a failed state + retry; reload shows
   no half-answer persisted.
5. Confirm SSE is not buffered by Traefik/forward-auth (chunks arrive live, not in
   one burst at the end).

## Phase 2 — Lossless terminal reconnect (agent-gateway + assistant-api + assistant-ui)

Build & test:

```bash
./gradlew :services:agent-gateway:test           # resume vs snapshot, epoch, tailer-from-offset
./gradlew :services:assistant-api:test           # query passthrough relay
(cd services/assistant-ui && npm run typecheck && npm run lint && npm run test)
```

Manual verify:

1. Start an agent session producing continuous output.
2. Sleep the tab / drop the socket for several seconds while output continues.
3. On reconnect: screen is NOT cleared, the gap output is appended exactly once,
   scrollback is intact (no dup, no skip).
4. Restart the session server-side, then reconnect with a stale offset → a full
   snapshot is delivered (screen clears once) rather than a wrong diff.
5. Disconnect longer than the retained log window → full snapshot fallback.

## Phase 3 — Session list/status SSE (assistant-api + assistant-ui)

```bash
./gradlew :services:assistant-api:test
(cd services/assistant-ui && npm run typecheck && npm run lint && npm run test)
```

Manual verify: open two tabs; start/stop a session in one → the other updates
within a few seconds with no refresh; leave one idle for minutes → stream stays
open (keepalive); confirm the old poll loop is gone (no repeating list/turns GETs).

## Phase 4 — Chat virtualization (assistant-ui only)

```bash
(cd services/assistant-ui && npm run typecheck && npm run lint && npm run test)
```

Manual verify: load a session with many messages; scroll + type stay smooth; a new
streaming message appends without re-rendering or jumping the whole list.

## End-to-end (Playwright system-tests)

```bash
./gradlew :services:system-tests:test   # streaming-chat + forced-reconnect-resume specs
```

## Regression guard (all phases)

Confirm unchanged: WS heartbeat (30s), reconnect backoff (500ms→10s), inactive-tab
reconnect gating, idle scale-down (`IdleScaleDownScheduler`), presence counting
(`ConnectedClientTracker`). These are explicitly preserved (FR-011/SC-007).

## PR conventions (per repo)

Each phase: `gh pr create --assignee ExtraToast --label enhancement`, impersonal
PR-body voice, no attribution/co-author trailers, unescaped backticks in the
heredoc. Render/validate is not triggered (no `fleet.yaml` change) unless an SSE
proxy-buffering manifest tweak proves necessary (separate, flagged).
