# Claude / Codex parity

Both CLIs are first-class throughout the stack. This document
records the explicit checkpoints so a future code change that
accidentally biases one CLI over the other is easy to spot.

## Image

`services/agent-runner/Dockerfile` installs both via npm:

```
RUN npm install -g \
      @anthropic-ai/claude-code \
      @openai/codex
```

Versions stay pinned via `npm install` at image build time; bump
both deliberately in the same PR so the auth refresh-ping job has
a chance to catch a regression in either before user workloads
do.

## Credentials

Two PVCs — `claude-credentials` mounted at `/home/agent/.claude`
and `codex-credentials` mounted at `/home/agent/.codex`. The
runner image sets `CODEX_HOME=/home/agent/.codex` so Codex picks
up the right path even if a future Codex release changes its
default home.

The `auth-bootstrap` Pod populates both with one human
exec-and-paste session each (see `README.md`). The
`agents-refresh-ping` CronJob exercises both every 6 hours.

## Domain / runtime

- `services/agent-gateway`: `AgentKind` has CLAUDE, CODEX, SHELL;
  `AgentSessionManager.commandFor` maps CLAUDE -> props.cli.claude
  and CODEX -> props.cli.codex.
- `services/assistant-api`: `WorkspaceAgentKind` mirrors the same
  three; `HttpAgentGatewayClient.spawnAgent` passes the enum
  straight through.
- `services/assistant-ui`: `AgentKindPicker` exposes all three as
  equal-weight options.

A new CLI plugs into the same set of seams; nothing in the
control plane assumes the agent is Claude.
