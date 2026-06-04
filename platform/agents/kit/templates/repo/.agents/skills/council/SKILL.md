---
name: council
description: Orchestrate a large, decomposable problem across two model families — Codex and Claude plan it independently, cross-critique for two rounds, a judge consolidates one plan plus a parallel task DAG, then cheap workers fan out execution in isolated worktrees. Use for big parallelizable work, not for small or tightly-coupled sequential changes.
---

# council

A cross-model orchestrator for problems too big for one pass. The driver is
`platform/agents/council/council.py` (stdlib-only; shells out to `codex exec`
and `claude -p`). Around it, you handle the human-facing part: clarify the brief
and present the two checkpoints.

Use it for large work that decomposes into independent parallel pieces and is
worth the spend (multi-agent uses roughly 15x the tokens of a single chat). Do
not use it for small or tightly-coupled, sequential work — handle those in a
normal session.

Loop:

1. Clarify. If scope, constraints, definition of done, or repo area are
   ambiguous, ask the user 2-3 targeted questions; otherwise skip. Write the
   result to `brief.md`.
2. Plan:
   ```bash
   python3 platform/agents/council/council.py plan --brief brief.md
   ```
   Two independent plans, two cross-critique rounds, one consolidation. Prints
   the run dir. Use `--estimate` for the call count first.
3. Checkpoint 1: show the user `consolidated_plan.md` and the `tasks.json` DAG
   from the run dir. Get approval or edits before any fan-out spend.
4. Fan out, on approval:
   ```bash
   python3 platform/agents/council/council.py fanout --run <run-dir>
   ```
   Cheap workers execute the DAG in isolated worktrees; results land on a
   `council/<run>/integration` branch. The working branch is untouched.
5. Checkpoint 2: present `report.md` — what merged, what failed, the integration
   branch to review. Collect feedback; re-loop into `plan` or re-run tasks.

Notes:

- Both `codex` and `claude` CLIs must be authenticated. Codex plans/critiques at
  reasoning effort high; the consolidator and verifier are Claude.
- Defaults: 2 rounds, planners `codex:gpt-5.5` + `claude:opus`, workers
  `claude:haiku` (hard tasks bumped to sonnet). Override with `--rounds`,
  `--planner-a`, `--planner-b`, `--max-workers`.
- Runs are resumable: stages are idempotent on their output files; re-run with
  `--run <dir>`.
- Full design: `docs/private/council-orchestrator-design.md`.
