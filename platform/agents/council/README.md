# council

Cross-model planning + fan-out orchestrator. Two different model families
(Claude + Codex) plan a problem independently, critique each other's plan for two
rounds, a single judge consolidates one plan plus a parallel task DAG, then cheap
worker agents execute the DAG in isolated git worktrees. The human is consulted
at two checkpoints: the clarified brief, and the consolidated plan before fan-out.

This script is the engine; the `council` skill (`.claude` / `.agents`) drives the
human-facing steps around it. Use it for **large, decomposable, parallelizable**
work only — multi-agent runs roughly 15x the tokens of a single chat and *hurts*
small or tightly-coupled tasks.

## Why this shape
Cross-*model* critique beats self-critique (different families make different
mistakes, and a model over-rates its own output); gains are front-loaded so two
rounds is the sweet spot; consolidation synthesises rather than votes; expensive
models plan/critique/judge while cheap models fan out. Full rationale and sources:
`docs/private/council-orchestrator-design.md`.

## Usage

```bash
# stages 1-4: produce a consolidated plan + tasks.json (no execution)
python3 platform/agents/council/council.py plan --brief brief.md

# preview cost (no model calls)
python3 platform/agents/council/council.py plan --estimate

# resume an interrupted run (stages are idempotent on their output files)
python3 platform/agents/council/council.py plan --brief brief.md --run .council/runs/<id>

# pure-function checks, no model calls
python3 platform/agents/council/council.py --self-test
```

`plan` prints the run dir on stdout. Inspect `consolidated_plan.md` and
`tasks.json` there. Fan-out execution is a separate `fanout` subcommand.

## Knobs
- `--rounds N` — critique rounds (default 2).
- `--planner-a cli:model`, `--planner-b cli:model` — override the planning tier
  (default `claude:opus` and `codex:gpt-5.5`).

## Run layout (`.council/runs/<id>/`, gitignored)
```
brief.md
planA.v1.json planB.v1.json            # stage 1
critique-of-A.r1.md planA.v2.json ...  # stage 2 (per round)
consolidated_plan.md tasks.json        # stage 4
state.json                             # resume marker
```

## Notes
- Sub-invocations run with `KB_AUTO_MCP_DISABLED=1` so council's internal prompts
  never pollute the knowledge base.
- Codex planning/critique uses `model_reasoning_effort=high`, read-only sandbox.
- Both `claude` and `codex` CLIs must be installed and authenticated.
- We don't bundle a full JSON-Schema validator; `validate_tasks` checks the
  fields fan-out relies on and runs a topological sort (cycle / unknown-dep
  detection). The JSON schemas under `schemas/` document the contract.
