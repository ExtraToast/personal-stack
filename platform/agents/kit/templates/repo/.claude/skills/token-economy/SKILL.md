---
name: token-economy
description: Use when reducing token usage, agent cost, context bloat, prompt-caching misses, RAG/LightRAG behavior, memory policies, or durable instructions. Also use when designing automatic KB recall so retrieval stays bounded.
---

# Token Economy

- Stable instructions belong in `AGENTS.md`, `CLAUDE.md`, or skills; volatile
  facts belong in the KB.
- Keep hook recall to `limit=3`; keep manual setup recall to `limit <= 5`.
- Use hybrid recall normally; use deep recall only after a miss or ambiguity.
- Keep runner MCP profiles narrow: `minimal` by default, wider profiles only
  when the task needs those extra tools.
- Preserve prompt-cache-friendly ordering: stable policy first, dynamic task
  context later.
- Summarize command output and avoid broad KB/context dumps.
