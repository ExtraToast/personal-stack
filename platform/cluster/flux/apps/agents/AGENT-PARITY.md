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

The `agents-login` portal keeps the two in lockstep too: its image
bakes both CLIs, the worker captures both credential sets in the same
login session, and it posts both provider payloads to agents-api
(`CLAUDE` with `oauth_token`, `CODEX` with `auth_json`/`config_toml`) —
a login flow that cannot refresh both providers is a parity regression.

The `auth-bootstrap` Pod populates both with one human
exec-and-paste session each (see `README.md`). The
`agents-refresh-ping` CronJob exercises both every 6 hours.

OAuth tokens persist on the PVCs, but the per-user flags that
record "first-run done" do not survive a fresh Pod on their own,
so the entrypoint seeds them on every boot:

- Claude reads theme + `hasCompletedOnboarding` + per-project
  `hasTrustDialogAccepted` from `~/.claude.json`, which sits beside
  (not inside) the `~/.claude` PVC and is otherwise lost on restart.
  `entrypoint.sh` restores the latest PVC-backed backup, then
  `jq //=` fills only the missing onboarding/theme/trust keys — a
  real restored config keeps its own theme, `oauthAccount`, and
  project history.
- Codex reads `projects."/workspace".trust_level` plus
  `approval_policy` / `sandbox_mode` from `~/.codex/config.toml` on
  the `codex-credentials` PVC. The entrypoint writes a
  trusted + non-interactive config only when none exists, so a
  hand-edited file on the PVC is never overwritten. Agent-gateway also
  passes `--dangerously-bypass-hook-trust` for the managed runner
  process so repo-managed hooks do not block startup on the interactive
  "Hooks need review" prompt.

Without this seeding every fresh session re-shows the theme picker
and onboarding wizard (Claude), the directory-trust prompt (Codex), or
the hook trust prompt (Codex), all of which block the non-interactive
tmux pane.

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

## Hooks, skills, and memory

`platform/agents/kit/manifest.yaml` is the checked-in parity ledger
for agent memory hooks, repo skills, installer-managed skills, and
canonical `knowledge.*` MCP tool names. The guard lives in
`AgentKitManifestTest`.

Claude and Codex both have repo-level `UserPromptSubmit`, `PreToolUse`,
and `Stop` hooks for bounded KB recall/capture. Codex loads lifecycle
hooks from `.codex/hooks.json` in trusted projects; the managed runner
passes `--dangerously-bypass-hook-trust` because the repo-managed hook
sources are vetted by `platform/agents/kit/manifest.yaml` and
`AgentKitManifestTest`.

A new repo hook, skill, or installer-managed surface is incomplete
unless it is listed in the manifest and passes the platform tooling
test. Any future agent-specific gap must carry an explicit unsupported
reason in the manifest so drift is visible instead of accidental.
