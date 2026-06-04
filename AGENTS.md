# personal-stack agent guide

This repo is a personal homelab GitOps monorepo. Claude uses `CLAUDE.md`;
Codex uses this file. Keep this file lean so project guidance does not crowd
out task context.

## Default workflow

- Consult the knowledge base before designing changes that depend on existing
  repo history, architecture, cluster behavior, secrets paths, or prior agent
  decisions. Use `knowledge.recall` with a distilled query, `scope` set to
  `project:personal-stack` when repo-specific, `mode=hybrid`, and `limit <= 5`.
- Escalate recall gradually: start with hits/snippets, call
  `knowledge.relations` around a promising hit, and fetch a full note only when
  the snippet/relation graph is insufficient.
- Capture durable lessons or decisions at the end of work with
  `knowledge.capture_lesson` or `knowledge.capture_decision`. Prefer
  `scope=project:personal-stack` for repo behavior and `topic:<slug>` for
  general tool/framework knowledge. If the scope is uncertain, omit it and let
  the curator classify.
- Keep token use bounded. Do not paste large files, full transcripts, or broad
  KB dumps into the answer. Prefer precise `rg` searches, small file windows,
  summaries of command output, and focused MCP calls.
- Commit only intentional changes, in small logical commits. Never stage or
  revert unrelated dirty files.
- Maintain Claude/Codex parity. Any project skill, hook, memory rule, or
  installer behavior added for Codex must get the Claude equivalent in the same
  branch, and vice versa.

## Verification

Run the smallest meaningful checks for the touched area:

- Kotlin service: `./gradlew :services:<service>:test`
- Platform inventory/rendering: `./gradlew :platform:tooling:test`
- Vue UI: `npm run typecheck && npm run lint && npm run test` inside the UI

When a command cannot run, say exactly why and what remains unverified.
