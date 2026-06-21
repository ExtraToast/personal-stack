# Feature Specification: Agent credential login portal

**Feature Branch**: `agents-login/*`
**Created**: 2026-06-21
**Status**: In progress
**Input**: Centralized, browser-driven re-authentication for the Claude Code
and Codex CLIs used by the agent runners, with credentials stored in Vault and
fanned out to every runner.

## Problem

The agent runners authenticate the Claude Code CLI and the Codex CLI via OAuth
(no API keys). The credentials live on `ReadWriteOnce` `local-path` PVCs
(`claude-credentials`, `codex-credentials`) pinned to a single node
(`enschede-gtx-960m-1`). Re-authentication requires `kubectl exec` into the
`auth-bootstrap` pod and copy/pasting a long OAuth URL out of a terminal that
cannot copy text (the agents-ui xterm relay). Consequences:

- Credentials can only be refreshed on the one node that owns the PVC; they
  cannot fan out to other hosts/runners.
- The copy-paste-out-of-terminal requirement blocks the refresh entirely when
  the only available terminal cannot copy.
- There is no single place to re-sign-in once and have every runner pick up the
  new credentials.

## User Scenarios & Testing

### User Story 1 — Re-sign-in from a browser (Priority: P1)

As the fleet operator, when the Claude or Codex credentials expire, I open a web
page, click through the OAuth flow in a real browser, and every runner picks up
the renewed credentials without my touching a terminal.

**Independent Test**: With expired credentials, the operator visits the portal,
completes the Claude login (authorize URL clickable, redirect URL pasted back in
the page) and the Codex device-code login, and the two Vault paths
(`secret/agents/claude-oauth`, `secret/agents/codex-oauth`) hold fresh,
non-empty credential material.

**Acceptance Scenarios**:

1. **Given** an authenticated operator with the `AGENTS_LOGIN` permission,
   **When** they start a Claude login, **Then** the page shows a copyable
   authorize URL and accepts the post-approval redirect URL, and on success the
   captured `.credentials.json` + `.claude.json` are written to Vault.
2. **Given** the same operator, **When** they start a Codex login, **Then** the
   page shows the device code and, after approval, the captured `auth.json` +
   `config.toml` are written to Vault.
3. **Given** a user **without** `AGENTS_LOGIN`, **When** they request the portal
   host, **Then** forward-auth denies them.

### User Story 2 — Credentials reach every runner (Priority: P1)

As a runner on any node, I read the current credentials from a Vault-projected
Secret rather than a single-node PVC, so I can be scheduled anywhere.

**Acceptance Scenarios**:

1. **Given** credentials in Vault, **When** VSO reconciles, **Then** the Secrets
   `agents-claude-oauth` / `agents-codex-oauth` exist in `agents-system` with
   the exact CLI filenames as keys.

### User Story 3 — Rotated refresh tokens survive (Priority: P2)

As the platform, when a CLI rotates its refresh token in place, the new value is
written back to Vault so all hosts converge instead of reverting to a stale
projected copy on the next pod restart.

**Acceptance Scenarios**:

1. **Given** a single-writer keeper holding the live session, **When** the
   on-disk credential rotates, **Then** the keeper writes it back to Vault under
   a Lease lock with KV v2 Compare-And-Set, and a CAS conflict retries rather
   than blind-overwriting.

## Non-goals

- Headless / fully-automated login. OAuth is inherently interactive and both
  CLIs require an active upstream subscription; the portal makes the interaction
  copy-paste-friendly, it does not remove the human step.
- API-key authentication (not purchasable for these CLIs).
- Changing the runner image in this feature's cluster-side work; the runner
  consuming the projected Secret is a companion change in the agents repo.

## Success Criteria

- A re-sign-in performed once in the browser renews credentials for every runner
  on every node.
- No terminal copy-paste is required.
- The OAuth credential paths in Vault are writable only by the portal worker and
  their version history cannot be destroyed by `agents-api`.

## Open Questions

- **Rotation invalidation**: do Anthropic/OpenAI invalidate the previous refresh
  token when the CLI rotates in place? Must be answered empirically with the
  exact pinned CLI versions; it gates whether runners may refresh locally or must
  be strictly read-only.
- **Propagation**: access-token TTL vs worst-case Vault→VSO `refreshAfter` +
  kubelet Secret-volume latency + runner boot. Runner cutover is gated on the TTL
  exceeding worst-case propagation.
- **Image-format parity** across the `agents-login`, `agent-runner`
  (auth-bootstrap/refresh-ping/kb-install), and orchestrator-launched runner
  images.
