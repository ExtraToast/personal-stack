---
name: agent-session-bootstrap
description: Use when configuring Claude Code or Codex sessions, hooks, skills, MCP servers, durable instructions, agent runners, or future-session defaults. Ensures KB recall/capture and token-efficient behavior are installed without relying on user reminders.
---

# Agent Session Bootstrap

## Checklist

1. Locate the active user and project config layers:
   - Claude: `~/.claude/settings.json`, `~/.claude/CLAUDE.md`, project
     `CLAUDE.md`, and `~/.claude/skills`.
   - Codex: `~/.codex/config.toml`, `~/.codex/hooks.json`, repo `AGENTS.md`,
     and `.agents/skills`.
2. Ensure the `knowledge` MCP server is configured and uses
   `KB_BEARER_TOKEN` rather than an inline secret where possible.
3. Register bounded recall hooks:
   - `UserPromptSubmit`: short prompt recall, `limit=3`, `mode=hybrid`.
   - `PreToolUse` for edits: path/module recall, deduped per session.
   - `Stop`: transcript digest with a per-session capture cap.
4. Keep hooks silent on KB failure and add `KB_AUTO_MCP_DISABLED=1` as a panic
   switch.
5. Add or update global memory files so future sessions know to consult and
   update the KB without user reminders.
6. Validate with dry-run hook payloads and at least one `tools/list` or
   `knowledge.recall` MCP call.

Do not put bearer tokens, secrets, or full transcripts into committed files.
