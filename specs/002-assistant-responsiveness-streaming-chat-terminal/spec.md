# Feature Specification: Assistant Responsiveness

**Feature Branch**: `002-assistant-responsiveness-streaming-chat-terminal`
**Created**: 2026-06-10
**Status**: Draft
**Input**: User description: improve responsiveness of the web-based assistant — eliminate input delay, desync, and lost output, and make it scale smoothly across devices, borrowing proven techniques from a custom xterm.js web-terminal.

## Overview

The web-based assistant has two interaction surfaces: a **chat** surface (ask the
assistant a question, get an answer) and an **agent terminal** surface (a live
shell/agent session rendered in the browser). Both feel sluggish today:

- Chat shows nothing until the entire answer is built, so a long answer feels
  like a hang.
- The terminal wipes and repaints the whole screen whenever the connection
  blips (tab sleep, network change, proxy timeout), which reads as flicker,
  scroll loss, and occasional desync.
- Session list and status are refreshed by repeated polling, which adds lag and
  scales poorly as a person opens more tabs/devices.
- Long chat histories get progressively janky to scroll and type into.

This feature makes both surfaces feel immediate and lossless, and keeps them
smooth as usage scales across devices.

## User Scenarios & Testing _(mandatory)_

### User Story 1 - Streaming chat answers (Priority: P1)

A person asks the assistant a question in chat. The answer begins appearing
within a moment and continues to fill in progressively as it is produced, rather
than appearing all at once after a long pause. The person can read along while
the rest is still being generated.

**Why this priority**: This is the most-felt source of "input delay." It is
contained to the chat path, delivers the largest perceived responsiveness win,
and is independently shippable without touching the terminal.

**Independent Test**: Send a prompt that produces a long answer; confirm the
first visible text appears well before the full answer is complete, and that the
final rendered answer is identical to what a non-streaming request would
produce.

**Acceptance Scenarios**:

1. **Given** an active chat session, **When** the person sends a message that
   yields a multi-paragraph answer, **Then** partial answer text becomes visible
   before generation finishes.
2. **Given** a streaming answer in progress, **When** generation completes,
   **Then** the message is marked complete and persisted, and reloading the
   session shows the same final text.
3. **Given** a streaming answer in progress, **When** the person navigates away
   and returns to the session, **Then** they see either the completed message or
   a correctly resumed/whole answer with no duplicated or missing text.
4. **Given** the assistant fails partway through generating an answer, **When**
   the error occurs, **Then** the person sees a clear failed state for that
   message and can retry, with no partial answer silently persisted as if
   complete.

---

### User Story 2 - Lossless terminal reconnect (Priority: P2)

A person is watching an agent session in the terminal. Their connection briefly
drops — the tab slept, the network changed, or a proxy idled the socket. On
reconnect, the terminal seamlessly continues: it shows exactly the output that
was produced during the gap, without clearing the screen, losing scrollback, or
showing duplicated output.

**Why this priority**: Eliminates the flicker/desync that makes the terminal
feel unreliable. Builds on the existing reconnect/heartbeat mechanics rather than
replacing them.

**Independent Test**: Stream continuous output in a session, force a disconnect
(sleep the tab / drop the socket) for several seconds while output continues,
then reconnect; confirm the terminal resumes mid-stream with no clear, no gap,
and no duplication.

**Acceptance Scenarios**:

1. **Given** a session producing output, **When** the socket drops and
   reconnects within the retained-output window, **Then** only the output
   produced during the gap is replayed and appended — the screen is not cleared.
2. **Given** a session that was restarted on the server while the client was
   away, **When** the client reconnects, **Then** the client receives a full
   fresh snapshot rather than an incorrect partial diff.
3. **Given** a disconnect longer than the retained-output window, **When** the
   client reconnects, **Then** it falls back to a full snapshot and clearly
   resumes from current state without claiming continuity it cannot guarantee.
4. **Given** a reconnect, **When** output resumes, **Then** no byte is shown
   twice and none is skipped, for the supported gap durations.

---

### User Story 3 - Live session list and status (Priority: P3)

A person has the assistant open, possibly across multiple tabs or devices. The
list of sessions and each session's status (starting, running, idle, stopped)
updates on its own as things change, without the person refreshing and without a
constant background polling drag.

**Why this priority**: Removes polling lag and improves multi-device scaling, but
is lower-impact on the core typing/reading experience than stories 1 and 2.

**Independent Test**: Open the assistant in two tabs; start/stop a session in one
and confirm the other reflects the new session and status promptly without a
manual refresh, and that an idle session holds its stream open without dropping.

**Acceptance Scenarios**:

1. **Given** the assistant open in one place, **When** a session is created,
   stopped, or changes status elsewhere, **Then** the list/status updates within
   a few seconds without a manual refresh.
2. **Given** an idle session view left open, **When** no changes occur for an
   extended period, **Then** the live updates channel stays connected through
   intermediary proxies.
3. **Given** multiple tabs/devices open, **When** the number of open views
   grows, **Then** server load from status updates does not grow in proportion
   to a high-frequency poll per view.

---

### User Story 4 - Smooth long chat histories (Priority: P3)

A person works in a chat session that has accumulated many messages. Scrolling
the history and typing into the message box stay smooth — there is no growing
lag as the session gets longer.

**Why this priority**: Protects the streaming win from being undone by render
cost on long sessions. Lower priority because it only bites established sessions.

**Independent Test**: Load a session with a large number of messages; confirm
scrolling stays smooth and typing latency does not visibly degrade compared to a
short session.

**Acceptance Scenarios**:

1. **Given** a session with many messages, **When** the person scrolls or types,
   **Then** interaction stays smooth without visible stutter.
2. **Given** a new message arrives or streams in, **When** it is appended,
   **Then** the rest of the history is not visibly re-rendered or jumped.

### Edge Cases

- What happens when the network drops mid-stream during a chat answer? (Answer
  must not be persisted as complete; person can retry.)
- What happens when two tabs view the same streaming chat answer at once?
- What happens when a terminal disconnect exactly straddles a server-side session
  restart (epoch change) — must yield a full snapshot, not a diff.
- What happens when retained terminal output exceeds the buffer limit during a
  long gap — oldest output is dropped and the client is told to take a full
  snapshot rather than receiving a partial replay.
- What happens to status updates when an intermediary proxy enforces an idle
  timeout shorter than the keepalive interval.
- What happens when a person opens many tabs/devices simultaneously — resource
  use must stay bounded.

## Requirements _(mandatory)_

### Functional Requirements

- **FR-001**: The chat surface MUST deliver answer content incrementally as it is
  produced, so partial text is visible before the full answer completes.
- **FR-002**: A streamed chat answer MUST be persisted as a single complete
  message on success, identical to the non-streamed result, and retrievable on
  reload.
- **FR-003**: A chat answer that fails mid-stream MUST surface a distinct failed
  state, MUST NOT be persisted as a complete answer, and MUST be retryable.
- **FR-004**: The terminal MUST retain a bounded amount of recent session output
  server-side, associated with a session-restart marker (epoch), sufficient to
  cover short disconnects.
- **FR-005**: On terminal reconnect, the client MUST be able to indicate how much
  output it has already shown (a position) and for which epoch, and the system
  MUST replay only the output produced after that position for the same epoch.
- **FR-006**: When the client's epoch does not match the current session, or its
  position is older than retained output, the system MUST deliver a full fresh
  snapshot instead of a partial replay.
- **FR-007**: Terminal reconnect MUST NOT clear the screen or lose scrollback when
  a valid partial replay is possible, and MUST NOT show duplicated or skipped
  output for supported gap durations.
- **FR-008**: Session list and per-session status MUST update on the client
  without manual refresh, via a server-pushed stream rather than client polling.
- **FR-009**: The session-status stream MUST stay open through typical
  intermediary proxies during idle periods.
- **FR-010**: The chat history view MUST render and update without cost that
  grows with the number of messages in the session for routine interactions
  (scroll, type, append).
- **FR-011**: Existing heartbeat, reconnect-backoff, and presence/idle behaviors
  MUST be preserved (not regressed) by these changes.
- **FR-012**: Streaming and replay MUST work for an authenticated person across
  multiple concurrent tabs/devices without cross-talk between sessions.
- **FR-013**: The work MUST be deliverable in phases, with streaming chat (User
  Story 1) shippable on its own ahead of the terminal and session-list changes.

### Key Entities _(include if feature involves data)_

- **Chat answer stream**: an in-progress assistant answer, with incremental
  chunks, a terminal completion or failure state, and a final persisted form.
- **Terminal session output buffer**: a bounded, ordered record of a session's
  recent output, tagged with a session epoch and a monotonic position so a client
  can request "everything after position N for epoch E."
- **Session epoch**: a marker that changes when a session is (re)started, used to
  decide between partial replay and full snapshot.
- **Client read position (offset)**: how far through a session's output a given
  client has already rendered.
- **Session-status event**: a pushed update describing a session's existence and
  lifecycle state (starting, running, idle, stopped).

## Success Criteria _(mandatory)_

### Measurable Outcomes

- **SC-001**: For chat answers, first visible answer content appears in under ~1
  second under normal conditions, versus waiting for the entire answer today.
- **SC-002**: A streamed answer's final persisted text matches the equivalent
  non-streamed answer 100% of the time (no truncation, duplication, or reorder).
- **SC-003**: For terminal disconnects within the retained-output window, 100% of
  reconnects resume without a screen clear and with zero duplicated or skipped
  output.
- **SC-004**: Terminal reconnects that previously repainted a full snapshot now
  transfer only the gap, materially reducing bytes transferred and eliminating
  the visible flicker on reconnect.
- **SC-005**: Session list/status reflects remote changes within a few seconds
  with no manual refresh, while per-view background request rate during idle is
  reduced to a low keepalive instead of continuous polling.
- **SC-006**: In a session with many messages, scroll and typing latency remain
  comparable to a short session (no perceptible degradation as length grows).
- **SC-007**: Existing reconnect, heartbeat, and idle/presence behaviors show no
  regression after the change.

## Assumptions

- The underlying agent/model layer can emit answer content incrementally; if a
  given backend cannot, chat falls back to delivering the whole answer as a
  single final chunk (still correct, just not progressive).
- "Bounded retained output" is sized to cover common short disconnects (tab
  sleep, network switch, proxy idle), not arbitrarily long offline periods; the
  exact size is a tuning decision settled during planning.
- Authentication, transport security, and network-level access controls already
  in place remain unchanged; this feature does not alter the auth model.

## Non-Goals

- Persisting full byte-accurate terminal transcripts as durable history (the live
  stream remains the source of truth; only a bounded replay buffer is added).
- Changing the agent/model capabilities, prompts, or the set of supported agents.
- Offline use beyond the bounded replay window.
- Reworking authentication or network/proxy infrastructure.
