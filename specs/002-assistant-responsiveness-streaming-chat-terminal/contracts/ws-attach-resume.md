# Contract: Terminal WS attach with epoch/offset resume (Phase 2, P2)

## Attach URL (browser → assistant-api → agent-gateway)

Browser connects to:

```
/api/v1/ws/sessions/{sessionId}/attach?epoch={E}&offset={O}
```

- `epoch` and `offset` are **optional**. Absent (first attach) → full snapshot.
- `assistant-api` (`SessionAttachHandler.resolveAttach`) appends the same
  `epoch`/`offset` query to the upstream gateway URI
  `/ws/agents/{gatewayAgentId}/attach?epoch={E}&offset={O}`. Otherwise it relays
  frames verbatim, unchanged.

## Gateway behavior (`AgentAttachHandler`)

On attach, read `epoch`/`offset` from the query. Let `currentEpoch` be the tmux
session's epoch, `logStart`/`logLength` the on-disk bounds.

```
RESUME  if epoch present && epoch == currentEpoch && logStart <= offset <= logLength:
    - do NOT send capture snapshot
    - start LogTailer at byte `offset`
    - first frame: control {"epoch": currentEpoch, "snapshot": false}

SNAPSHOT otherwise (no epoch, epoch mismatch, or offset evicted/out of range):
    - send capture snapshot (current behavior)
    - start LogTailer at EOF
    - first frame: control {"epoch": currentEpoch, "snapshot": true}
```

## Frame envelope (additive, backward-compatible)

Outbound (gateway → client), unchanged shape plus optional `off`:

```json
{ "output": "...utf8...", "off": 12345 }
```

`off` = the log byte offset _after_ this chunk. Control frame (sent once on
attach, before/with the first output):

```json
{ "epoch": 7, "snapshot": false }
```

Inbound (client → gateway) is unchanged: `{"input","enter"}`,
`{"resize":{"cols","rows"}}`, `{"heartbeat":true}`.

Old clients that ignore `off`/`epoch`/`snapshot` keep working (they just always
behave as today). assistant-api relays all of these verbatim.

## Client behavior (`sessionSocket.ts` + `SessionTerminal.vue`)

- Track latest `off` and `epoch` from inbound frames.
- On reconnect, include `?epoch=&offset=` in the attach URL.
- On the `snapshot:true` control frame → `term.clear()` then write (full repaint).
  On `snapshot:false` → **do not clear**; append the replayed gap then live output.
- If `epoch` changes vs the stored one → adopt new epoch, treat as snapshot.
- Keep existing heartbeat (30s), reconnect backoff (500ms→10s), and the inactive-
  tab reconnect gating unchanged.

## Acceptance mapping

- FR-005/FR-007/SC-003: resume within window → no clear, gap-only replay, no dup.
- FR-006: epoch mismatch or evicted offset → full snapshot.
- SC-004: bytes transferred on reconnect ≈ gap size, not full screen.
