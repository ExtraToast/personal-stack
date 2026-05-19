# agents domain

Per-workspace agent-runner Pods plus the one-time auth bootstrap that
keeps Claude Code and Codex CLI logged in across container restarts
without ever shipping API keys.

## Topology

All workloads live in a single `agents-system` namespace because the
credential PVCs (`claude-credentials`, `codex-credentials`) are
namespace-scoped and both the auth-bootstrap Pod that populates them
and the per-workspace runner Pods that consume them must share that
namespace. The NetworkPolicy uses the
`app.kubernetes.io/part-of: agent-runner` label to apply a tight
egress allow list (DNS, in-cluster knowledge stack, outbound 443) to
just the runner Pods; the bootstrap Pod stays permissive so the
interactive OAuth flows can reach the upstream IdPs.

## First-time auth bootstrap

The credential PVCs (`claude-credentials`, `codex-credentials`) start
empty. Populate them once by execing into the long-lived bootstrap
Pod:

```
kubectl -n agents-system exec -it auth-bootstrap -- bash
# inside the pod:

# Claude Code — prints an OAuth URL; complete in a laptop browser.
# The verification code lands in claude@jorisjonkers.dev.
claude /login

# Codex — device-code flow, finish on laptop.
codex login --device

# Sanity:
claude -p 'say hi' --output-format text
codex exec --no-stream 'say hi'
ls -la ~/.claude/.credentials.json ~/.codex/auth.json
```

After both files exist, every per-workspace Pod that mounts the same
PVCs reuses the session. Refresh tokens rotate in-place on the
volume — that's why the mounts are RW.

## Health

The `agents-refresh-ping` CronJob fires every 6h and runs the same
sanity prompts. If either CLI fails to answer, the Job exits non-zero
and a loki rule (see `apps-observability-rules`) alerts. That's the
canary for "tokens went stale" — the fix is a re-run of the manual
flow above.

## Image

The runner image lives at `ghcr.io/extratoast/personal-stack/agent-runner:latest`
and bundles claude, codex, tmux, git, gh, language toolchains, plus
the agent-gateway sidecar jar. The same image powers the auth Pod,
the refresh ping, and every per-workspace runner — version skew
between auth and run time is structurally impossible.

## Why not API keys?

The user has working Claude.ai + ChatGPT Plus subscriptions; both
CLIs unlock without paying twice via the OAuth flow. API keys would
double-bill the same usage.
