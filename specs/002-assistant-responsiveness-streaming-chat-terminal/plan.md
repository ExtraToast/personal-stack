# Implementation Plan: Assistant Responsiveness

**Branch**: `002-assistant-responsiveness-streaming-chat-terminal` | **Date**: 2026-06-10 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/002-assistant-responsiveness-streaming-chat-terminal/spec.md`

## Summary

Make both assistant surfaces feel immediate and lossless, in four stacked PRs,
P1 first:

- **Phase 1 (P1) — Streaming chat answers.** Chat answers are not generated
  server-side today: `ChatSessionKind.PLAIN` only persists messages, and the
  `KNOWLEDGE` kind explicitly reserved answer "Pod binding + streaming" for a
  follow-up. This phase *is* that follow-up, built streaming-first: a new SSE
  endpoint generates a `KNOWLEDGE`-session answer from the existing RAG stack
  (`RetrievalPort`/`LightRagClient`), streams chunks to the browser as they
  arrive, and persists one complete assistant `ChatMessage` on success. LightRAG
  exposes `/query/stream` (NDJSON), so the answer is genuinely progressive.
- **Phase 2 (P2) — Lossless terminal reconnect.** The gateway already mirrors the
  reference web-terminal: tmux `pipe-pane` writes raw PTY output to an
  append-only, disk-capped log, and `LogTailer` streams new bytes by file
  **offset**. Output keeps being written to the log even while no browser is
  attached. The only missing pieces are (a) an **epoch** per tmux session and
  (b) honoring a client-supplied `(epoch, offset)` on attach to resume the
  tailer mid-log instead of always sending a fresh snapshot + tailing from EOF.
- **Phase 3 (P3) — Live session list/status via SSE**, replacing client polling
  of `GET .../turns`/session lists.
- **Phase 4 (P3) — Keyed + virtualized chat list** so long sessions stay smooth.

## Technical Context

**Language/Version**: Kotlin (Spring Boot 4) for `assistant-api` and
`agent-gateway`; TypeScript 6 + Vue 3 for `assistant-ui`.
**Primary Dependencies**: `spring-boot-starter-web` (Spring MVC, servlet — not
WebFlux) + `spring-boot-starter-websocket`; Spring `RestClient` (sync); JOOQ +
Flyway + PostgreSQL; Vue 3.5, Pinia 3, Vite 8, PrimeVue 4 (ships
`VirtualScroller`), xterm.js 6 + `@xterm/addon-fit`; `@extratoast/vue-web-commons`
fetch wrapper (cookies + `X-XSRF-TOKEN`).
**Storage**: PostgreSQL (`chat_sessions`, `chat_messages`); terminal output lives
only in the gateway's per-session tmux log (no DB change for Phase 2).
**Testing**: `./gradlew :services:assistant-api:test` and
`:services:agent-gateway:test` (JUnit5 + Testcontainers); UI `npm run typecheck
&& npm run lint && npm run test` (Vitest); Playwright `system-tests` for the
reconnect/streaming e2e.
**Target Platform**: k3s pods behind Traefik + forward-auth; browser.
**Project Type**: mixed (two JVM services + one Vue UI).
**Performance Goals**: SC-001 first chat token < ~1s; SC-003 zero clear/dup on
reconnect within the retained-log window; SC-005 status latency a few seconds
with idle keepalive instead of polling.
**Constraints**: Servlet stack → use `SseEmitter`/`ResponseBodyEmitter` (not
`Flux`); EventSource cannot carry CSRF/cookies cleanly → UI consumes SSE via
`fetch()` + `ReadableStream`; keep the gateway envelope (`{input}`/`{output}`)
backward-compatible; preserve heartbeat (30s), reconnect backoff (500ms→10s),
`ConnectedClientTracker`/`WorkspaceActivityTracker` and `IdleScaleDownScheduler`.
**Scale/Scope**: single operator, a handful of concurrent tabs/devices; retained
log window sized to cover tab-sleep / network-switch gaps (tens of seconds).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] No attribution is introduced in files, comments, commit text, or PR text.
- [x] Claude/Codex parity preserved — N/A: no agent-facing skills/hooks/installer
      behavior changes; this is product code (services + UI).
- [x] Rendered artifacts: no `fleet.yaml`/Traefik render source is touched. SSE
      and the existing WS already route through the assistant host; no new public
      host. (If a proxy buffering tweak proves necessary it is a deploy-manifest
      change, flagged in research, not a fleet render.)
- [x] Small stacked PR boundary is clear — one PR per phase, P1 shippable alone
      (FR-013). Phases 2–4 are independent of Phase 1 and of each other.
- [x] Verification command identified per area (see Testing above; per-phase in
      quickstart.md).

## Project Structure

### Documentation

```text
specs/002-assistant-responsiveness-streaming-chat-terminal/
|-- plan.md
|-- research.md
|-- data-model.md
|-- quickstart.md
|-- contracts/
|   |-- chat-answer-stream.md        # Phase 1 SSE contract
|   |-- ws-attach-resume.md          # Phase 2 epoch/offset attach contract
|   `-- session-status-sse.md        # Phase 3 status stream contract
`-- checklists/requirements.md
```

### Source Code (real paths this feature touches)

```text
# Phase 1 — streaming chat (assistant-api + assistant-ui)
services/assistant-api/src/main/kotlin/.../assistant/
  application/query/ChatAnswerStreamService.kt        # NEW: orchestrates retrieve->stream->persist
  application/rag/ContextBuilder.kt                    # reuse to build the prompt context
  domain/port/RetrievalPort.kt                         # reuse (LightRagClient)
  infrastructure/integration/LightRagClient.kt         # ADD streaming retrieve (/query/stream)
  infrastructure/web/ChatSessionController.kt          # ADD POST .../messages:stream (SseEmitter)
  domain/model/ChatMessageRole.kt / ChatSessionKind.kt # route KNOWLEDGE answers
services/assistant-ui/src/features/sessions/
  services/chatSessionsService.ts                      # ADD streamAnswer() fetch+ReadableStream
  stores/chatSessions.ts                               # ADD streaming append + failed/retry state
  components/ChatTab.vue (or equivalent)               # render partial + failed/retry affordance

# Phase 2 — terminal offset/epoch (agent-gateway + assistant-api + assistant-ui)
services/agent-gateway/src/main/kotlin/.../agentgateway/
  tmux/AgentSession.kt / AgentSessionManager.kt        # ADD epoch per session
  tmux/LogTailer.kt                                    # ADD start-at-offset constructor/param
  ws/AgentAttachHandler.kt                             # parse ?epoch&offset; resume vs snapshot; emit epoch+offset
services/assistant-api/src/main/kotlin/.../assistant/
  infrastructure/ws/SessionAttachHandler.kt            # pass epoch/offset query through to upstream URI
services/assistant-ui/src/features/workspaces/
  services/sessionSocket.ts                            # track (epoch,offset); send on reconnect
  components/SessionTerminal.vue                        # clear ONLY on snapshot signal, not every reopen

# Phase 3 — session-list/status SSE (assistant-api + assistant-ui)
services/assistant-api/.../infrastructure/web/        # NEW SSE controller for session/status events
services/assistant-api/.../application/idle/          # emit status changes to subscribers
services/assistant-ui/src/features/{sessions,workspaces}/ # subscribe to SSE, drop polling

# Phase 4 — chat virtualization (assistant-ui only)
services/assistant-ui/src/features/sessions/components/ # PrimeVue VirtualScroller + stable :key
```

**Structure Decision**: Hexagonal layout already in place
(`domain/application/infrastructure`) is kept. New streaming orchestration is an
application service behind the existing `RetrievalPort`; the SSE/WS edges live in
`infrastructure/web` and `infrastructure/ws`. Phase 2 keeps the gateway "dumb"
(byte log + offset) and adds only epoch bookkeeping; assistant-api stays a verbatim
relay plus query passthrough.

## Phase 0: Outline & Research

Unknowns resolved in `research.md`:

1. **Chat answer source & streaming** — confirmed LightRAG `/query/stream` returns
   NDJSON and disables proxy buffering (`X-Accel-Buffering: no`); fallback to
   non-streaming `/query` emitted as a single SSE chunk if streaming is disabled
   (matches spec assumption). Decide PLAIN vs KNOWLEDGE behavior.
2. **SSE on a servlet stack** — `SseEmitter` + a bounded async executor; set
   `X-Accel-Buffering: no`; confirm Traefik/forward-auth do not buffer
   `text/event-stream`.
3. **Browser SSE consumption with CSRF** — `fetch()` + `ReadableStream` reader
   (POST carries `X-XSRF-TOKEN`), not `EventSource`.
4. **Epoch source in the gateway** — derive from tmux-session (re)creation; how it
   composes with the existing log-truncation reset in `LogTailer.poll()`.
5. **Offset reporting to the client** — extend the output envelope with an optional
   running `off` (and one-time `epoch`) without breaking the verbatim relay.
6. **Chat virtualization** — PrimeVue 4 `VirtualScroller` (no new dependency).

**Output**: `research.md`

## Phase 1: Design & Contracts

1. Entities in `data-model.md`: streaming answer lifecycle, gateway epoch/offset,
   client read position, status event. Note the **no-new-migration** result for
   Phase 2 and whether Phase 1 needs a `status`/`pending` column on `chat_messages`
   (decision recorded there).
2. Contracts in `contracts/`: SSE chat-answer stream (event names `chunk`,
   `done`, `error`); WS attach resume query + control frames; status SSE.
3. `quickstart.md`: per-phase build/test/verify commands and manual reconnect /
   streaming checks.
4. Re-run Constitution Check (unchanged — still product code, small PRs).

**Output**: `data-model.md`, `contracts/*`, `quickstart.md`

## Phase 2: Task Planning Approach

`/speckit.tasks` should emit four task groups matching the phases, ordered, each a
self-contained PR:

- Group A (P1): backend streaming service + SSE endpoint + LightRAG streaming +
  persistence/failed-state, then UI consumption + render. Tests: service unit +
  controller SSE test + Vitest store test + one Playwright streaming check.
- Group B (P2): gateway epoch + tailer-from-offset + attach resume + envelope
  offset; assistant-api query passthrough; UI offset/epoch tracking + no-clear.
  Tests: gateway resume/snapshot unit tests; UI sessionSocket test; Playwright
  forced-disconnect resume check.
- Group C (P3): status SSE producer + subscriber wiring; drop polling. Tests:
  emitter test + UI subscription test.
- Group D (P4): VirtualScroller + stable keys. Tests: Vitest render + a perf smoke.

Mark Group A as the only must-ship-first; B/C/D independent. Within each group,
backend tasks precede the UI task that consumes them; tests co-located with code.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
| --- | --- | --- |
| None | — | — |

No constitution gate is intentionally violated. The work reuses existing ports,
the existing tmux/log/offset machinery, and adds no new public host or render
source.

## Progress Tracking

**Phase Status**:

- [x] Phase 0: Research complete
- [x] Phase 1: Design complete
- [x] Phase 2: Task planning approach complete

**Gate Status**:

- [x] Initial Constitution Check: PASS
- [x] Post-Design Constitution Check: PASS
- [x] All NEEDS CLARIFICATION resolved (none remained; tuning values deferred to
      implementation per spec Assumptions)
