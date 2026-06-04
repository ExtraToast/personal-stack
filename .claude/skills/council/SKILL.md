---
name: council
description: Orchestrate a large, decomposable problem across two model families — Claude and Codex plan it independently, cross-critique for two rounds, a judge consolidates one plan plus a parallel task DAG, then cheap workers fan out execution in isolated worktrees. Use for big parallelizable work; NOT for small or tightly-coupled/sequential changes (use a normal session for those).
---

# council

A cross-model orchestrator for problems too big for one pass. The driver is
`platform/agents/council/council.py` (stdlib-only; shells out to `claude -p` and
`codex exec`). Your job around it is the human-facing part: clarify the brief
and present the two checkpoints.

## When to use / when not

- **Use it** when the work is large, decomposes into independent parallel
  pieces, and is worth the spend (multi-agent runs roughly 15x the tokens of a
  single chat).
- **Do not use it** for small changes, or for tightly-coupled / sequential work
  where every step depends on the last — multi-agent *hurts* there. Just do
  those in a normal session.

## The loop

1. **Clarify (you → user).** Read the request. If scope, constraints,
   definition-of-done, or the repo area are ambiguous, ask 2-3 targeted
   questions with `AskUserQuestion`. Skip if already clear. Write the result to
   a `brief.md`.
2. **Plan (council.py).** Run the `plan` phase — two independent plans, two
   cross-critique rounds, one consolidation:
   ```bash
   python3 platform/agents/council/council.py plan --brief brief.md
   ```
   It prints the run dir. (`--estimate` first if you want the call count.)
3. **Checkpoint 1 (you → user).** Show the user `consolidated_plan.md` and the
   `tasks.json` DAG from the run dir. Let them approve or edit before any fan-out
   spend. This is the cheap gate before the expensive wave.
4. **Fan out (council.py).** On approval:
   ```bash
   python3 platform/agents/council/council.py fanout --run <run-dir>
   ```
   Cheap workers execute the DAG in isolated worktrees; results land on a
   `council/<run>/integration` branch. Your working branch is untouched.
5. **Checkpoint 2 (you → user).** Present `report.md`: what merged, what failed,
   the integration branch to review. Collect feedback; re-loop into `plan` or
   re-run specific tasks as needed.

## Notes

- Both `claude` and `codex` CLIs must be authenticated. Codex plans/critiques at
  reasoning effort high; the consolidator and verifier are Claude.
- Defaults: 2 critique rounds, planners `claude:opus` + `codex:gpt-5.5`, workers
  `claude:haiku` (consolidator bumps hard tasks to sonnet). Override with
  `--rounds`, `--planner-a`, `--planner-b`, `--max-workers`.
- Runs are resumable: stages are idempotent on their output files; re-run with
  `--run <dir>` to continue.
- Full design and rationale: `docs/private/council-orchestrator-design.md`.
