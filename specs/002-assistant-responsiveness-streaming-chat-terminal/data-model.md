# Data Model: Assistant Responsiveness

This feature is mostly transient streaming state. Only Phase 1 may touch
persistent storage; Phase 2 needs **no DB change**.

## Persistent entities

### ChatMessage (existing — `chat_messages`)

Current columns (via `JooqChatMessageRepository` / `ChatMessage` model):
`id`, `sessionId`, `role` (`ChatMessageRole`), `body`, `createdAt`.

**Phase 1 change — to decide during implementation, recorded here:**

- A streamed answer is persisted as a **single** assistant `ChatMessage` only on
  successful completion (FR-002). A failed stream persists **nothing** (FR-003).
  This needs no schema change — the in-progress answer lives in the browser and in
  the SSE emitter, not the DB.
- Optional (only if we want a failed answer to survive reload as a retryable row):
  add a nullable `status` column (`COMPLETE` default) to `chat_messages` via a new
  Flyway migration. **Default decision: do not add it.** Keep failed answers
  client-side and retryable in-session; this keeps Phase 1 a code-only,
  no-migration change and matches "persist only on success."

No other persistent entity changes.

## Transient entities (no storage)

### Chat answer stream (Phase 1)

Lifecycle of one in-progress assistant answer:

| State       | Meaning                                               | Transition                   |
| ----------- | ----------------------------------------------------- | ---------------------------- |
| `streaming` | chunks arriving from LightRAG `/query/stream`         | start on SSE open            |
| `complete`  | full text assembled + persisted as a `ChatMessage`    | `done` event                 |
| `failed`    | error/disconnect before completion; nothing persisted | `error` event / reader abort |

Server holds the accumulating `StringBuilder` per emitter; client holds the
partial text on the in-progress message object in the Pinia store.

### Gateway session epoch + offset (Phase 2)

Per tmux/agent session, held in `agent-gateway` memory (alongside the existing
`AgentSession`):

- **epoch** (`Long`): assigned at tmux-session creation; changes only when the
  session/log is (re)created. Used to decide resume-vs-snapshot.
- **logStart / logLength** (bytes): derived from the on-disk log file each poll;
  `logStart` rises when the disk-capped log is front-truncated.
- The client's **read position** is the byte `offset` it last received, paired with
  the `epoch` it received it under. Held in `assistant-ui` `sessionSocket.ts`,
  never persisted.

Resume decision (gateway, on attach with `?epoch=E&offset=O`):

```
if E == currentEpoch && logStart <= O <= logLength:
    skip snapshot; start LogTailer at byte O; emit {epoch:E, snapshot:false}
else:
    send capture snapshot; start LogTailer at EOF; emit {epoch:currentEpoch, snapshot:true}
```

### Session-status event (Phase 3)

Pushed event describing a session's existence + lifecycle. Derived from existing
domain state (`WorkspaceAgentSessionStatus`: STARTING/RUNNING/STOPPED, plus idle
state from `WorkspaceActivityTracker`). Shape (illustrative):

```json
{ "sessionId": "<uuid>", "status": "RUNNING", "idle": false, "ts": "<iso>" }
```

No storage; produced from in-memory trackers and repository reads, fanned out to
SSE subscribers.

## Wire envelopes (see contracts/)

- Chat SSE events: `chunk`, `done`, `error`.
- Terminal WS frames (additive): outbound `{output, off?}` plus a one-time
  `{epoch, snapshot}` control frame; attach query `?epoch=&offset=`.
- Status SSE events: `session` (upsert), `removed`, plus keepalive comments.
