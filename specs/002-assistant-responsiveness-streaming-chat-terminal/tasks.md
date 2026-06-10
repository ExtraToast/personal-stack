# Tasks: Assistant Responsiveness

**Input**: Design documents from `/specs/002-assistant-responsiveness-streaming-chat-terminal/`
**Prerequisites**: plan.md, research.md, data-model.md, contracts/, quickstart.md

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no ordering dependency).
- **[Story]**: US1 (streaming chat, P1) / US2 (terminal reconnect, P2) / US3
  (status SSE, P3) / US4 (chat virtualization, P3).
- One stacked PR per group. **US1 is the only must-ship-first**; US2/US3/US4 are
  independent of US1 and of each other.

## Phase 1: Setup

- [ ] T001 Confirm branch + spec dir; identify per-area validation commands
      (`:services:assistant-api:test`, `:services:agent-gateway:test`, UI
      `typecheck && lint && test`, `:services:system-tests:test`).
- [ ] T002 [P] Confirm `rag.*` config + LightRAG reachability assumptions in
      `services/assistant-api/.../config/RagProperties.kt` for the streaming path.

## Phase 2: Foundational (regression guard — applies to all groups)

- [ ] T003 Capture the current heartbeat/reconnect/presence behavior as the
      regression baseline: WS heartbeat 30s, backoff 500ms→10s, inactive-tab
      reconnect gating (`services/assistant-ui/src/features/workspaces/services/sessionSocket.ts`),
      `ConnectedClientTracker`/`WorkspaceActivityTracker`/`IdleScaleDownScheduler`
      (assistant-api `application/idle/`). No change here — these MUST stay green
      (FR-011/SC-007).

---

## Phase 3: User Story 1 — Streaming chat answers (P1) → PR #1

**Goal**: Chat answers stream in progressively; persisted only on success;
failed/retryable on error. **Independent Test**: send a long-answer prompt; first
text < ~1s; reload equals streamed text; mid-stream network kill → failed + retry,
nothing half-persisted. Contract: `contracts/chat-answer-stream.md`.

### Backend (assistant-api) — precede UI

- [ ] T004 [US1] Add streaming retrieval to
      `services/assistant-api/.../infrastructure/integration/LightRagClient.kt`:
      POST `/query/stream`, expose incremental chunks via
      `RestClient.exchange { _, res -> res.body.bufferedReader().lineSequence() }`;
      keep existing `/query` as the non-streaming fallback.
- [ ] T005 [US1] Add a bounded streaming executor (extend
      `services/assistant-api/.../config/AsyncConfig.kt` or a new config) so model
      latency cannot exhaust the common async pool.
- [ ] T006 [US1] Create
      `services/assistant-api/.../application/query/ChatAnswerStreamService.kt`:
      persist USER message (reuse `AppendChatMessageCommand`), build context via
      `ContextBuilder`/`RetrievalPort`, stream chunks to a sink, accumulate full
      text, persist one ASSISTANT `ChatMessage` on success, persist nothing on
      failure.
- [ ] T007 [US1] Add `POST /api/v1/chat-sessions/{id}/messages:stream` returning
      `SseEmitter` to
      `services/assistant-api/.../infrastructure/web/ChatSessionController.kt`:
      headers `text/event-stream` + `Cache-Control: no-cache` +
      `X-Accel-Buffering: no`; events `chunk`/`done`/`error`; complete/
      completeWithError + onTimeout/onCompletion deterministic finalize.
- [ ] T008 [US1] Service unit test for `ChatAnswerStreamService` (success persists
      one assistant message = concatenated chunks; failure persists nothing) in
      `services/assistant-api/src/test/.../application/query/`.
- [ ] T009 [US1] Controller SSE test (event sequence, headers, error path) in
      `services/assistant-api/src/test/.../infrastructure/web/`.

### Frontend (assistant-ui) — consumes the above

- [ ] T010 [US1] Add `streamAnswer(id, body)` to
      `services/assistant-ui/src/features/sessions/services/chatSessionsService.ts`
      using `fetch` POST (credentials + `X-XSRF-TOKEN`) + `ReadableStream` reader +
      SSE frame parser yielding `{type:'chunk'|'done'|'error'}`.
- [ ] T011 [US1] Extend
      `services/assistant-ui/src/features/sessions/stores/chatSessions.ts` with a
      streaming send: placeholder assistant message (`streaming`), append chunks,
      `complete` on `done` (swap persisted id), `failed` on error/abort + retry.
- [ ] T012 [US1] Render partial answer + failed/retry affordance in the chat
      component (`services/assistant-ui/src/features/sessions/components/*` — locate
      the chat view that renders `detail.messages`).
- [ ] T013 [P] [US1] Vitest store test (streaming append / complete / failed+retry)
      in `services/assistant-ui/src/features/sessions/__tests__/`.

### E2E

- [ ] T014 [US1] Playwright streaming spec (first text before completion; reload
      equals; mid-stream kill → failed) in `services/system-tests`.

**PR #1**: `--assignee ExtraToast --label enhancement`. Validate with T008/T009 +
UI checks + T014.

---

## Phase 4: User Story 2 — Lossless terminal reconnect (P2) → PR #2

**Goal**: Reconnect replays only the gap, no clear/dup/skip; epoch mismatch →
snapshot. **Independent Test**: drop socket mid-output for seconds → resume with no
clear/dup; server restart + stale offset → full snapshot. Contract:
`contracts/ws-attach-resume.md`.

### Gateway (agent-gateway) — precede assistant-api + UI

- [ ] T015 [US2] Add an `epoch` per tmux session at creation in
      `services/agent-gateway/.../tmux/AgentSession.kt` +
      `tmux/AgentSessionManager.kt` (monotonic / creation-derived).
- [ ] T016 [US2] Add a start-at-offset option to
      `services/agent-gateway/.../tmux/LogTailer.kt` (begin at byte O instead of
      EOF; keep front-truncation reset; never split UTF-8).
- [ ] T017 [US2] Implement resume-vs-snapshot in
      `services/agent-gateway/.../ws/AgentAttachHandler.kt`: parse `?epoch&offset`;
      RESUME (skip snapshot, tail from O) when epoch matches and offset in
      `[logStart,logLength]`, else SNAPSHOT (tail from EOF); emit one-time control
      `{"epoch","snapshot"}` and add running `{"off"}` to output frames (additive).
- [ ] T018 [US2] Gateway unit tests: resume path (no snapshot, gap-only), snapshot
      fallback (epoch mismatch / evicted offset), `off` accounting, UTF-8 boundary
      across resume, in `services/agent-gateway/src/test/.../`.

### assistant-api passthrough

- [ ] T019 [US2] In
      `services/assistant-api/.../infrastructure/ws/SessionAttachHandler.kt`, read
      `epoch`/`offset` from the client attach URI and append them to the upstream
      gateway URI in `resolveAttach`; keep frame relay verbatim.
- [ ] T020 [US2] assistant-api relay test: epoch/offset query forwarded to upstream;
      frames (incl. `off`/control) relayed unchanged, in
      `services/assistant-api/src/test/.../infrastructure/ws/`.

### Frontend (assistant-ui)

- [ ] T021 [US2] In
      `services/assistant-ui/src/features/workspaces/services/sessionSocket.ts`,
      track latest `(epoch, off)` from frames and append `?epoch=&offset=` on
      reconnect; keep heartbeat/backoff/inactive-tab gating unchanged.
- [ ] T022 [US2] In
      `services/assistant-ui/src/features/workspaces/components/SessionTerminal.vue`,
      clear the terminal ONLY on a `snapshot:true` control frame (and on epoch
      change); on `snapshot:false` append without clearing.
- [ ] T023 [P] [US2] Vitest test for `sessionSocket` offset/epoch tracking + resume
      query in `services/assistant-ui/.../__tests__/`.

### E2E

- [ ] T024 [US2] Playwright forced-disconnect spec (resume no-clear/no-dup; restart
      → snapshot) in `services/system-tests`.

**PR #2**: `--assignee ExtraToast --label enhancement`.

---

## Phase 5: User Story 3 — Live session list/status via SSE (P3) → PR #3

**Goal**: List/status push without polling; idle stream survives proxies.
Contract: `contracts/session-status-sse.md`.

- [ ] T025 [US3] Status producer fanning lifecycle (STARTING/RUNNING/STOPPED) +
      idle (`WorkspaceActivityTracker`/`ConnectedClientTracker`) to a bounded set of
      `SseEmitter` subscribers with periodic keepalive comments, in
      `services/assistant-api/.../application/idle/` + a new
      `infrastructure/web` SSE controller `GET /api/v1/sessions/events`.
- [ ] T026 [US3] Emitter/producer unit test (initial snapshot, delta on status
      change, removed event, keepalive) in `services/assistant-api/src/test/...`.
- [ ] T027 [US3] UI: subscribe via `fetch`+`ReadableStream`, update session
      list/status store, remove the existing poll loop, reconnect with backoff
      (`services/assistant-ui/src/features/{sessions,workspaces}/`).
- [ ] T028 [P] [US3] Vitest subscription/update test in the UI feature tests.

**PR #3**: `--assignee ExtraToast --label enhancement`.

---

## Phase 6: User Story 4 — Chat virtualization (P3) → PR #4

**Goal**: Long histories stay smooth; append doesn't re-render the list.

- [ ] T029 [US4] Replace the chat message `v-for` with PrimeVue `VirtualScroller`
      and a stable message-id `:key` in the chat component
      (`services/assistant-ui/src/features/sessions/components/*`). No new dep.
- [ ] T030 [US4] Ensure streaming updates mutate only the in-progress message
      object (no full-list re-render); verify with the Phase 1 streaming path.
- [ ] T031 [P] [US4] Vitest render test (large list renders windowed; append
      stable) in the UI feature tests.

**PR #4**: `--assignee ExtraToast --label enhancement`.

---

## Phase 7: Validation (per phase, before each PR)

- [ ] T032 Run the phase's build/test commands from `quickstart.md`; do the manual
      verify steps for that phase.
- [ ] T033 Regression guard (T003): heartbeat/reconnect/presence/idle behaviors
      unchanged (FR-011/SC-007).
- [ ] T034 SSE-through-edge check for US1/US3: confirm Traefik + forward-auth do
      not buffer `text/event-stream` (chunks arrive live).

## Dependencies & parallelism

- Order within a group: backend → UI that consumes it → e2e. Tests co-located.
- Cross-group: US2/US3/US4 do **not** depend on US1; they may proceed in any order
  after US1 ships (or in parallel branches if reviewer bandwidth allows).
- `[P]` tasks (T002, T013, T023, T028, T031) touch separate files from their
  group's critical path and can run alongside it.

## Summary

- **Total tasks**: 34 (T001–T034).
- **Parallelizable `[P]`**: 5.
- **Groups / PRs**: 4 (US1 first, independently shippable; US2/US3/US4 independent).
- **Next**: `/speckit.analyze` for a consistency pass, or `/speckit.implement`
  starting with Phase 3 (US1) for the recommended chat-streaming-first delivery.
