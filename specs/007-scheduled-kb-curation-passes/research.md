# Research: Scheduled KB Curation Passes

*All claims verified against the personal-stack-2 / knowledge-vault trees on 2026-06-24.*

## Existing in-cluster patterns (reused)

- **Headless Claude CronJob** — `platform/cluster/flux/apps/agents/refresh-ping/cronjob.yaml`: `agent-runner` image, `claude-credentials` PVC at `/home/agent/.claude`, `HOME=/home/agent`, node `enschede-gtx-960m-1`, `runAsUser 1000`, restores `~/.claude.json` from PVC backups, runs `claude -p ... --output-format text`.
- **KB bearer for in-cluster MCP** — `kb-install/cronjob.yaml`: `KB_BEARER_TOKEN` from secret `agents-kb-bearer` key `bearer`; in-cluster URL `http://knowledge-api.knowledge-system.svc.cluster.local:8080`.
- **Knowledge MCP config shape** — `mcp/agents-mcp-servers-configmap.yaml`: `{"type":"http","url":"<KB>/mcp","headers":{"Authorization":"Bearer <token>"}}`.

## knowledge-vault write mechanism (Tier-2)

From `platform/cluster/flux/apps/knowledge/knowledge-ingest-worker/deployment.yaml`:

- Repo: `git@github.com:ExtraToast/knowledge-vault.git` (`VAULT_CLONE_URL`).
- Auth: ed25519 **deploy key**, Vault `secret/knowledge-system/vault-deploy-key` field `private_key`, injected to a file and used as `VAULT_SSH_KEY_PATH`. The worker pulls-before/pushes-after; humans + LiveSync write the same tree from their own clones.
- The deploy-key-VSS comment confirms the deploy-key model is deliberate: repo-scoped, write-toggleable, Vault-rotated.

**Decision**: Tier-2 in `agents-system` injects this deploy key via a new `VaultStaticSecret` and does a **fresh ephemeral SSH clone** (not the RWO `knowledge-vault-clone` PVC, which is bound by the worker). It commits to a `curator/weekly-<date>` branch and pushes.

## PR creation: decision + alternatives

- A **deploy key can push but cannot open a PR** (no API scope).
- agents-api exposes an **installation-token endpoint** the agent-runner `gh` wrapper uses (`credentials/github-app-vss.yaml`), but it is unconfirmed whether the GitHub App is installed on `knowledge-vault` with PR-write.
- **Chosen (MVP)**: push the branch via deploy key, then emit the GitHub **compare URL** (`https://github.com/ExtraToast/knowledge-vault/compare/main...curator/weekly-<date>?expand=1`) to Job logs. The operator opens the PR with one click. This fully satisfies "human-gated, single proposal, never auto-merged" without depending on App installation, and is deterministic.
- **Follow-up**: auto-open via `gh pr create` using an installation token, once App-on-knowledge-vault is confirmed.

## Topic vocabulary (FR-009)

Now in knowledge-api Postgres (`db/migration/V2__topic_vocabulary.sql`, `V3__seed_topics.sql`, `V7__project_vocabulary.sql`; `domain/Topic.kt`), managed via `AdminMcpTools` — a curator-governance tool that `KNOWLEDGE_MODE=lite` drops. So no authoritative vocabulary read over MCP today. Tier-2 therefore consolidates only **observed in-use** tags/slugs from its bounded result set and routes anything uncertain to `_needs-review` (never invents). A read-only vocabulary tool in lite mode is a separate knowledge-api change that would unlock full canonicalization.

## Risks

- **R1 — Vault path for the deploy key** *(resolved: agents/-path)*: the VSS reads `secret/agents/knowledge-vault-deploy-key` so the existing agents-system VSO policy covers it (no cross-namespace grant). One-time setup copies the same key `knowledge-ingest-worker` uses into that path (`vault kv put`); until populated the VSS does not sync and the Tier-2 Job fails loudly.
- **R2 — GitHub App on knowledge-vault**: auto-PR is deferred until confirmed; MVP uses compare-URL, so this does not block.
- **R3 — Headless MCP tool permissions**: `claude -p --allowed-tools mcp__knowledge__...` must auto-allow without prompts; validate on first dry-run (Tier-1 already exercises this).
- **R4 — Constitution I (attribution)**: PRs #723/#724 included `Co-Authored-By`/generated-by text, violating the no-attribution principle. Corrected by amending those commits and PR bodies; all 007 commits carry no attribution.
