---
name: kb-first
description: Use before designing or changing behavior that may depend on prior knowledge-base captures, repo history, architecture decisions, cluster state, agent conventions, or remembered lessons. Also use near task completion to capture durable lessons or decisions without dumping large KB context.
---

# KB First

Use the KB as a small retrieval layer, not as a large context dump.

1. Distill the task into a short query.
2. Call `knowledge.recall` with `mode=hybrid` and `limit <= 5`.
3. Prefer a specific `project:<repo>` or `topic:<slug>` scope.
4. Read snippets first, relations next, and full notes only when needed.
5. Capture durable lessons or decisions at the end. Never capture secrets,
   raw logs, full diffs, or full transcripts.
