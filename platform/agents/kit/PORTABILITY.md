# Agent Kit Portability

This runbook keeps local agent memory tooling movable without adding a second
durable memory backend. The durable source remains `knowledge-api` plus the
git-backed `knowledge-vault`; agent homes are reinstallable clients.

## Install And Uninstall

Install into the current user's client homes:

```bash
curl -fsSL -H "Authorization: Bearer ${KB_BEARER_TOKEN}" \
  "${KB_URL}/install.sh" | bash -s -- --agent all --scope user
```

Install into a repository checkout for project-local hooks and skills:

```bash
curl -fsSL -H "Authorization: Bearer ${KB_BEARER_TOKEN}" \
  "${KB_URL}/install.sh" | AGENT_KIT_PROJECT_ROOT=/workspace \
  bash -s -- --agent all --scope project
```

`--scope user` writes to `CLAUDE_CONFIG_DIR` or `~/.claude` and `CODEX_HOME`
or `~/.codex`. `--scope project` writes to `.claude` and `.codex` under
`AGENT_KIT_PROJECT_ROOT` or the current directory and ignores the user-home
overrides.

Uninstall uses the same `--agent` and `--scope` values:

```bash
curl -fsSL -H "Authorization: Bearer ${KB_BEARER_TOKEN}" \
  "${KB_URL}/install.sh" | bash -s -- --agent all --scope user --uninstall
```

Preview any install or uninstall with `--dry-run`.

## Doctor

Run the local doctor before moving an agent home or debugging MCP drift:

```bash
platform/agents/kit/render-agent-kit.py --doctor --require-live-kb
```

The doctor is read-only. It checks generated file drift, manifest version,
MCP profile parity, local Claude/Codex install manifests, `tools/list`, and a
bounded `knowledge.recall(mode=fast, limit=1)` call.

## Export And Backup

Use layered backups. Do not export bearer tokens, Vault tokens, raw transcripts,
or agent state logs.

| Layer | Canonical export | Purpose |
| --- | --- | --- |
| Human-readable memory | `knowledge-vault` git clone | Portable Markdown notes, promoted captures, topic paths, and reviewable history. |
| Operational KB state | Postgres logical backup, for example `pg_dumpall` from the backup CronJob | `kb_notes`, embeddings, relations, audit rows, recall counters, inbox state, and admin metadata. |
| Graph/document RAG cache | Rebuild from promoted vault content | LightRAG state is derived. Recreate it from the vault after restoring the core KB. |
| Agent kit clients | Re-run `/install.sh` | Hooks, skills, settings, allowlists, and `.knowledge-system-version` manifests are generated client state. |
| Secrets | Vault/1Password backup paths, not this repo | MCP bearer tokens, deploy keys, and service credentials. |

The `knowledge-vault` repository is the canonical human-readable export.
Postgres is the canonical operational restore source. A Basic Memory or
OpenMemory adapter should only be added for a real migration or comparison
exercise; neither replaces `knowledge-api` as the personal-stack memory API.

## Restore Order

1. Restore Vault and the service secrets required by Postgres, `knowledge-api`,
   curator, ingest worker, and the vault deploy key.
2. Restore Postgres from the latest logical backup.
3. Restore or reclone `knowledge-vault` and verify the curator can push to it.
4. Deploy `knowledge-api`, `knowledge-curator`, `knowledge-ingest-worker`, and
   LightRAG. Let derived graph/RAG stores rebuild from the promoted vault.
5. Reinstall agent homes with `/install.sh --agent all --scope user` or
   `/install.sh --agent all --scope project`.
6. Run `render-agent-kit.py --doctor --require-live-kb` and a small
   `knowledge.recall` smoke query.

## Compatibility Matrix

Versioned as of 2026-06-04.

| Surface | Current contract | Compatibility signal |
| --- | --- | --- |
| Agent kit manifest | `platform/agents/kit/manifest.yaml`, `version: 1` | `AgentKitManifestTest` pins generated paths and hashes; renderer `--check` must pass. |
| Installer | `/install.sh`, generated from `platform/agents/kit/templates/installer/install.sh.tpl` | Supports `--agent claude|codex|all`, `--scope user|project`, `--dry-run`, and `--uninstall`; writes `.knowledge-system-version` with `scope=`. |
| Claude Code | Hook scripts under `${CLAUDE_CONFIG_DIR:-~/.claude}/hooks` for user scope or `.claude/hooks` for project scope | Installer prints the settings snippet; repo mirrors keep Claude/Codex parity checked by tests. |
| Codex | Hooks under `${CODEX_HOME:-~/.codex}/hooks` plus `hooks.json`, or `.codex` for project scope | Installer writes parseable `hooks.json`; tests assert UserPromptSubmit, PreToolUse, and Stop commands. |
| Agent runner image | Managed MCP profiles selected through `AGENT_MCP_PROFILE` | Doctor checks runner entrypoint profile names against Claude and Codex ConfigMap blocks and reports tool counts. |
| Knowledge MCP schema | Canonical dot-name tools such as `knowledge.recall` and `knowledge.capture_lesson` | Hook tests assert canonical tool calls; doctor requires `knowledge.recall` in `tools/list` and calls it through `tools/call`. |
| LightRAG | Derived graph/RAG leg over promoted vault content | Rebuild from the vault after restore; do not mix LightRAG vectors with `knowledge-api` embeddings. |

Update this matrix in the same branch as any change to CLI hook support, MCP
tool names, runner profile names, installer flags, or agent-kit manifest
version.
