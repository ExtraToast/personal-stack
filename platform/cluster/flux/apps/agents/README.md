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

The credential PVCs are backed by the cluster-default `local-path`
StorageClass (RWO) — Longhorn is not installed here. Every Pod that
mounts the credentials therefore pins to **`enschede-gtx-960m-1`**
via `nodeSelector: personal-stack/node: enschede-gtx-960m-1`. RWO
permits multiple Pods on that single node to mount the volume
simultaneously, so concurrent per-workspace runners + the long-lived
bootstrap Pod + the refresh-ping CronJob still work, as long as they
all land on the same node. The runner Pods also require
`personal-stack/capability-docker-socket=true` because they mount
that node's `/var/run/docker.sock` for Docker CLI and Testcontainers
workflows. That mount is host-equivalent access; the namespace
enforces Pod Security `privileged` explicitly because `baseline`
forbids hostPath sockets. The orchestrator default and every
manifest in this directory hard-codes that node; moving the agent
host means flipping the selector in five places (the credential
PVCs, the auth-bootstrap Pod, the refresh-ping and kb-install
CronJobs, and the `AGENT_RUNTIME_NODE` env on the assistant-api
Deployment), plus the Docker socket gid and runner egress node IP.

## Re-authentication

The browser-driven login portal (`agents-login`, controller + worker
Deployments in this directory) is the preferred way to refresh
credentials: it runs the CLI logins server-side and writes the captured
OAuth credentials to agents-api, which persists them in Postgres — no
terminal copy-paste, one sign-in for the whole fleet. See `SETUP.md` for
the portal flow, the internal ingest model, and the node-pin
classification. The terminal bootstrap below remains the fallback until
the companion runner change (in the `agents` repo) makes runners consume
credentials from agents-api instead of the PVCs.

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
codex exec --skip-git-repo-check 'say hi' </dev/null
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
