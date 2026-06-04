---
name: token-economy
description: Use when the user asks to reduce token usage, agent cost, context bloat, prompt-caching misses, RAG/LightRAG behavior, memory policies, or durable instructions. Also use when installing many skills or designing automatic KB recall so retrieval stays bounded.
---

# Token Economy

## Rules

- Keep stable instructions in `AGENTS.md`, `CLAUDE.md`, or skills; keep volatile
  facts in the KB and retrieve them on demand.
- Prefer progressive disclosure: list/search first, open small file ranges next,
  fetch full files or notes only when needed.
- Keep recall bounded: default to `limit=3` for hook-injected context and
  `limit <= 5` for manual task setup.
- Use hybrid retrieval for normal KB recall. Escalate to deep retrieval only
  after a miss, ambiguity, or non-obvious cross-topic dependency.
- Keep runner MCP profiles narrow. Use `minimal` by default and opt into
  `frontend`, `cluster`, `code-intel`, or `full-diagnostic` only for tasks that
  need those extra tools.
- Do not install or enable low-fit skills just to grow the list. Skill metadata
  itself consumes prompt budget and very large skill sets can hide useful skills.
- Preserve prompt-cache-friendly ordering when writing durable instructions:
  stable policy first, dynamic task-specific context later.

## Output Discipline

When reporting command results, summarize only the lines needed to support the
decision. When explaining research, cite sources but do not paste long passages.

## Automatic Capture

Session digests should capture only reusable lessons above a confidence floor.
Use per-session caps and dedupe against existing KB hits before writing.
