---
name: agent-session-bootstrap
description: Use when configuring Claude Code or Codex sessions, hooks, skills, MCP servers, durable instructions, agent runners, or future-session defaults. Ensures KB recall/capture and token-efficient behavior are installed without relying on user reminders.
---

# Agent Session Bootstrap

Checklist:

1. Confirm `knowledge` MCP is configured with `KB_BEARER_TOKEN`.
2. Register `UserPromptSubmit`, edit/commit `PreToolUse`, and `Stop` hooks.
3. Keep hooks silent on KB failure and controlled by `KB_AUTO_MCP_DISABLED=1`.
4. Add project/global memory files so future sessions consult and update the KB.
5. Validate with a small hook payload and a `knowledge.recall` call.

Every Codex project skill, hook, or durable instruction must have an equivalent
Claude implementation in the same branch. Treat Codex-only `.agents`/`.codex`
files as incomplete until `.claude`/`CLAUDE.md`/installer parity exists.
