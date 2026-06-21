# Implementation Plan: Agent credential login portal

**Branch**: `agents-login/*` | **Date**: 2026-06-21 | **Spec**: [spec.md](./spec.md)

## Architecture

- **Self-contained `agents-login` image, built in this repo.** A single Node
  image that installs pinned Claude Code + Codex CLIs and runs in two modes:
  - **controller** — public, SSO-protected via forward-auth, holds **no** Vault
    token and stores **no** credential files; thin UI + proxy to the worker.
  - **worker** — private, single instance, owns the PTY, runs the CLI login,
    captures the credential files, and writes them to Vault via its own
    ServiceAccount.
  Building it here (rather than in the agents repo) means the portal ships
  before the companion runner change.
- **HTTP polling, not WebSocket.** The OAuth flow idles >100s while the operator
  approves in a browser; the Cloudflare orange-cloud edge kills idle WebSockets
  ~100s. The controller↔browser channel is short polling + POSTed stdin.
- **Standalone Node service**, not a pnpm-workspace member, with its own
  Dockerfile and a dedicated CI job (the pnpm `-r` frontend jobs are Vue-tuned).

## Vault schema

- `secret/agents/claude-oauth` — fields `credentials_json`, `claude_json` (+
  `schema_version`, `updated_at`, `updated_by`).
- `secret/agents/codex-oauth` — fields `auth_json`, `config_toml` (+ same
  metadata). Underscored field names because `vault kv put` with leading-dot
  keys is awkward; the dot-named filenames (`.credentials.json`, `.claude.json`,
  `auth.json`, `config.toml`) exist only in the projected Secret, emitted by VSS
  transformation templates. `.claude.json` is a SIBLING of `.claude/`.

## RBAC

- `agents-api` narrowed from `secret/{data,metadata}/agents/*` to the deploy-key
  subtrees (`agents/repositories/*`, legacy `agents/projects/*`).
- New `agents-oauth-writer` policy: `create/read/update` on the two OAuth data
  paths and `read` on their metadata (CAS) — no delete/destroy. Bound to the
  `agents-login-worker` SA in `agents-system`.

## Writeback consistency

Writer Deployment `replicas:1` + `Recreate` + a named Kubernetes `Lease` lock +
Vault KV v2 CAS against `metadata.version`. CAS loser re-reads and retries with
bounded backoff; persistent conflict pages rather than blind-overwrites.

## Migration (PVC-authoritative until cutover, no flag day)

During overlap the PVCs stay authoritative for live runners and
`agents-refresh-ping`. The worker writes both Vault AND the PVCs so existing
consumers keep working. An import/compare Job seeds Vault from PVC contents and
asserts equality. Then deploy VSO projections, resolve the rotation/propagation
gates, land the companion runner PR, canary one Secret-backed runner, switch all
runner creation, then retire the PVCs.

## De-pin classification

`AGENT_RUNTIME_NODE=enschede-gtx-960m-1` is multi-reason (docker-socket GID,
hard-coded egress callback IP, untrusted-workloads policy). Removing the
credential justification alone does NOT make runners schedule anywhere; full
de-pin is deferred to after the companion runner PR.

## Security

Reachable only behind forward-auth with a dedicated `AGENTS_LOGIN` permission
(not reused `AGENTS`). Controller enforces the permission from forward-auth
headers, holds no Vault token, uses CSRF tokens, `Cache-Control: no-store`, body
redaction, short session TTL. Controller↔worker authenticated with an internal
shared token on top of NetworkPolicy.

## Companion agents-repo seam (out of scope here)

Cluster exposes `agents-claude-oauth` / `agents-codex-oauth` projected Secrets
read-only. The companion runner entrypoint creates writable `$HOME/.claude` /
`$CODEX_HOME` and copies/symlinks the projected files into place;
`Fabric8AgentRunnerOrchestrator.kt` swaps the two PVC volume sources for Secret
sources + the init-copy.
