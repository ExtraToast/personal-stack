# `council` — cross-model orchestration skill (design)

Status: **proposed, awaiting approval to build.** Not yet committed.

A new agent-kit skill + portable driver that turns a single problem into a
two-model debate, consolidates the result into one parallelizable plan, then
fans the plan out across cheap worker agents and reconciles their work — pausing
for the human only at the two checkpoints that matter.

---

## 1. What it does (the loop)

```
            ┌─────────────────────────── you ───────────────────────────┐
            │                                                            │
   problem ─┤  0. clarify (2-3 small Qs, only if underspecified)         │
            │        ↓ brief.md                                          │
            │  1. dual independent plans   Opus  ║  gpt-5.5   (parallel) │
            │  2. cross-critique round 1   each reviews the OTHER's plan │
            │  3. cross-critique round 2   revise again                  │
            │  4. consolidate              one Opus judge → plan + DAG   │
            │        ↓                                                   │
   CHECKPOINT 1 ── show consolidated plan + task DAG ──→ approve / edit ─┤
            │        ↓                                                   │
            │  5. fan-out execution   N cheap workers, isolated worktrees│
            │  6. verify + reconcile  adversarial check, integrate, test │
            │        ↓                                                   │
   CHECKPOINT 2 ── final report (diffs, tests, failures) ──→ feedback ───┘
                     ↓ optional re-loop into stage 4 or targeted re-runs
```

Between every stage the state is plain JSON/Markdown in a run directory, so the
run is resumable, debuggable, and the hand-offs are structured (not free-text
chat) — which the failure-mode literature flags as the single biggest lever.

---

## 2. Why this shape — the research

Each design choice below is backed by a source; full notes with URLs are at the
end. Headline findings:

| Choice | Evidence |
|---|---|
| **Two *different model families* plan + critique** (not two Claudes) | Self-preference bias is real and measurable — LLM judges rate their own / same-family output higher. Cross-model ensembles win precisely because the families make *different* mistakes ("failure-mode diversity"). |
| **Exactly 2 critique rounds** | Self-Refine and multi-agent-debate gains are front-loaded: most improvement lands in rounds 1–2, saturates by ~3. Round 3 mostly buys cost + collapse risk. |
| **Critique must be adversarial, not "looks good"** | Sycophancy causes *agreement collapse* — debate can score **below** a single agent when critics rubber-stamp. Mitigation: explicit adversarial role, force concrete weaknesses, don't let "consensus" terminate early. |
| **Consolidate by *synthesis*, not voting** | LLM-Blender: rank-then-*fuse* (graft the best parts of each candidate) beats pick-one, because the best plan varies by sub-problem. |
| **Expensive models plan/critique/judge; cheap models fan-out** | Anthropic's Opus-lead + Sonnet-workers beat single-Opus by **90.2%**; 3-tier routing cuts cost **40–60%**. But *cheap-everywhere backfires* (retries/rework) — match model to task difficulty. |
| **A clarification gate before planning** | Models recognise ambiguity but rarely ask unprompted; "fail to ask for clarification" is a named multi-agent failure mode (MAST FM-2.2). Make it uncertainty-aware: ask only when underspecified. |
| **A verification gate after fan-out** | MAST's top failure category is verification/termination (premature termination, missing/incorrect verification). Each task carries its own verify; an adversarial checker confirms the diff matches the objective. |
| **Checkpoint *before* fan-out** | Multi-agent burns ~**15× the tokens** of a single chat. A cheap human checkpoint on the consolidated plan catches misalignment before the expensive wave. |
| **Bounded, difficulty-scaled fan-out** | Anthropic heuristic: 1 worker for simple lookups, 2–4 for comparisons, 10+ only for genuinely complex decomposition. Practitioner-reported coordination plateau ~4 agents on anything not embarrassingly parallel. |
| **Descriptive task delegation** | Each worker task = objective + output format + tool/source guidance + clear boundaries (Anthropic + OpenAI both rank instruction quality the #1 success factor). |
| **Don't use it for small/sequential work** | Multi-agent *hurts* tightly-coupled tasks and "most coding tasks" where agents must share context. Council is for **large, decomposable, parallelizable** work only — the SKILL.md says so explicitly. |

---

## 3. Architecture

### 3.1 Substrate — a portable driver script

`council.py` (Python 3, **stdlib only**, mirroring `render-agent-kit.py`). It
shells out to both CLIs and is engine-agnostic — it runs identically whether the
*host* session is Claude Code or Codex, satisfying the repo's Claude/Codex parity
rule. No dependency on the Workflow tool (which only runs under Claude).

Engine adapter (one function per CLI, parallelism via
`concurrent.futures.ThreadPoolExecutor`):

```
run_claude(prompt, model, *, schema=None, cwd=None, system=None) -> {text, json?}
    claude -p --model <opus|sonnet|haiku>
           [--output-format json] [--append-system-prompt <sys>]
           [--permission-mode acceptEdits]        # workers only
run_codex(prompt, model, *, schema=None, cwd=None) -> {text, json?}
    codex exec -m gpt-5.5 -c model_reasoning_effort=<high|medium>
           [--output-schema <file>] -o <last.txt> --json
           -C <cwd> --skip-git-repo-check
```

Both return a normalized record; `schema` routes through `--output-schema`
(codex) / `--output-format json` + validation (claude) so inter-stage state is
**structured**. Each call has a timeout, one retry, and captured stderr.

### 3.2 Division of labour: script vs host agent

The script is **non-interactive** (good for headless parity). Human interaction
lives in the host agent, driven by the SKILL.md:

| Step | Who | How |
|---|---|---|
| 0. clarify | host agent | `AskUserQuestion` (Claude) / equivalent (Codex); writes `brief.md` |
| 1–4 plan→consolidate | `council.py plan` | autonomous, parallel, exits with plan written |
| Checkpoint 1 | host agent | presents `consolidated_plan.md` + `tasks.json`; on approval re-invokes script |
| 5–6 fan-out→verify | `council.py fanout` | autonomous, parallel, exits with `report.json` |
| Checkpoint 2 | host agent | presents report; collects feedback; optional re-loop |

Two subcommands (`plan`, `fanout`) with the host mediating the checkpoints keeps
the script deterministic and the human-in-the-loop clean.

### 3.3 Model tiers (grounded in what's installed)

- **Planner / Critic A:** `claude --model opus`
- **Planner / Critic B:** `codex -m gpt-5.5 -c model_reasoning_effort=high`
- **Critic assignment is crossed:** Opus critiques B's plan; gpt-5.5 critiques A's plan.
- **Consolidator:** single strong cross-family judge — `claude --model opus`.
- **Workers:** `claude --model haiku` by default; the consolidator tags any
  `hard` task up to `sonnet`. (User's spec: fan-out is "claude agents with cheap models".)
- **Clarifier:** host model, no extra call.

All model IDs and reasoning efforts are config knobs at the top of `council.py`
(and overridable per run), not hard-coded magic.

### 3.4 Stage detail

**0. Intake & clarify.** Host normalises the request; asks 2–3 targeted
questions *only* if scope / constraints / definition-of-done / repo area are
ambiguous. Output `brief.md`: objective, constraints, definition of done,
in/out of scope, repo paths.

**1. Dual independent plans (parallel, expensive).** A and B each get the brief +
repo context and independently emit a plan against a fixed schema
`{summary, approach, steps[], risks[], parallelizable_tasks[], open_questions[]}`.

**2. Cross-critique round 1.** Critic = the *other* model, prompted as an
adversarial reviewer ("the plan is guilty until proven innocent; list concrete,
specific weaknesses; if you find none you are not looking hard enough; do not
compliment"). Each author then revises its own plan given the critique.

**3. Cross-critique round 2.** Repeat once on the v2 plans → v3.

**4. Consolidation.** One Opus judge receives both final plans + the critique
history and *synthesises* a single plan, grafting the strongest elements of each.
Emits:
- `consolidated_plan.md` — human-readable.
- `tasks.json` — a DAG. Each task:
  `{id, title, objective, output_format, paths[], depends_on[], difficulty: trivial|moderate|hard, model, verify, boundaries}`.
  Tasks with no unmet `depends_on` form each parallel wave. The judge is told to
  carve **non-overlapping file boundaries** so worktrees don't collide.

**Checkpoint 1 → you.** Approve / edit / reject before any fan-out spend.

**5. Fan-out execution (parallel, cheap, isolated).** Topologically sort
`tasks.json` into waves. Within a wave, run workers concurrently
(`min(max_workers, cores−2)`), each in its **own `git worktree`**. Each worker:
`claude -p --model <tier> --permission-mode acceptEdits` with a descriptive
prompt (objective + output format + boundaries + verify). It implements in its
worktree, runs the task's verify, returns
`{task_id, diff, files_changed, test_output, status, notes}`.

**6. Verify + reconcile.** Per result: run its verify; an adversarial checker
(sonnet/opus) confirms the diff actually satisfies the objective (guards MAST's
missing/incorrect-verification modes). Then merge worktree diffs onto an
integration branch in dependency order, detect conflicts, run the full suite
once integrated. Failed/conflicted tasks retry once with feedback, else surface.

**Checkpoint 2 → you.** Report: what was built, per-task diffs, test results,
failures, integration branch name. Feedback can re-loop into stage 4 (re-plan) or
trigger targeted stage-5 re-runs.

### 3.5 State & resumability

Run dir `.council/runs/<timestamp>-<slug>/` (gitignored):

```
brief.md  planA.v1.json planB.v1.json  critA→B.v1.md ...  planA.v3.json
consolidated_plan.md  tasks.json
workers/<task_id>/{prompt.md, result.json, worktree-path}
report.json  state.json   # which stage completed — enables --resume
```

`council.py --resume <run>` re-reads `state.json` and continues from the last
completed stage.

### 3.6 Cost guardrails (the ~15× warning, made operational)

- `council.py plan --estimate` prints planned agent count + rough token budget
  **before** spending; `fanout --estimate` does the same for the wave.
- Hard human checkpoint before the expensive fan-out wave.
- `--max-workers`, `--max-rounds` (default 2), `--worker-model` knobs.
- Difficulty-scaled, **bounded** fan-out; if the wave is truncated by
  `--max-workers`, it is logged (no silent caps).
- SKILL.md gates usage: *don't* invoke council for small or sequential changes —
  use a normal session. The planning phase alone is ~11 expensive model calls;
  that cost only pays off on large, decomposable work.

---

## 4. Files to create

Kit-managed (rendered into `.claude` + `.agents`, pinned in `manifest.yaml`):

1. `platform/agents/kit/templates/repo/.claude/skills/council/SKILL.md` — Claude-flavoured protocol + when-to-use / when-NOT.
2. `platform/agents/kit/templates/repo/.agents/skills/council/SKILL.md` — Codex-flavoured (`apply_patch` vs `Edit`, etc.).
3. `platform/agents/kit/templates/repo/.agents/skills/council/agents/openai.yaml` — Codex interface metadata (`display_name`, `short_description`, `default_prompt`; no MCP deps).

Plain repo code (outside `template_root`, so not rendered — ordinary committed files):

4. `platform/agents/council/council.py` — the driver (stdlib-only).
5. `platform/agents/council/prompts/{planner,critic,consolidator,worker,verifier}.md` — editable stage prompts (no code change to tune wording).
6. `platform/agents/council/schemas/{plan,tasks,worker_result}.schema.json` — used with codex `--output-schema` and validated in the claude path.
7. `platform/agents/council/README.md` — short operator note (subcommands, knobs, resume).

Wiring:

8. `manifest.yaml` — add `council` to `skills` (`supported_agents: [codex, claude]`, two `targets` with SHA256) and to `managed_paths` (the two SKILL.md + openai.yaml). Repo-only skill → **no** `installer` block.
9. `.gitignore` — `.council/runs/`.
10. `council.py --self-test` — pure-function checks (DAG topo-sort, wave planning, schema parse) runnable in CI without spending tokens.

Regenerate + validate:

```bash
python3 platform/agents/kit/render-agent-kit.py --write
python3 platform/agents/kit/render-agent-kit.py --check
python3 platform/agents/kit/render-agent-kit.py --doctor
python3 platform/agents/council/council.py --self-test
./gradlew :platform:tooling:test            # AgentKitManifestTest parity guard
```

---

## 5. Limitations (flagged up front)

- **~15× token cost.** Inherent to multi-agent; gated by checkpoints + estimate mode. Not for small jobs.
- **Worktree fan-out needs genuinely independent tasks.** Overlapping edits conflict at reconcile; the consolidator must carve non-overlapping file boundaries, and reconcile handles residue (retry / surface).
- **Multi-agent hurts tightly-coupled / sequential work.** Council explicitly declines those — SKILL.md routes them to a normal session.
- **Headless workers** (`claude -p`, `codex exec`) inherit the worktree's repo skills/hooks but not the host conversation; tasks must be self-contained (that's why delegation is descriptive).
- **Both CLIs must be authenticated** in the environment (they are here: `claude` + `codex` 0.137.0, model `gpt-5.5`).
- **Codex-as-host parity:** the script is identical for both, but the Codex SKILL.md and `openai.yaml` must ship in the same PR (parity rule). Codex's clarify/checkpoint UX uses its own prompt surface, not `AskUserQuestion`.

---

## 6. Build plan (on approval)

1. `council.py` skeleton: engine adapters, run-dir/state, `--self-test`, `--estimate`. 
2. Stages 1–4 (`plan` subcommand) + schemas + prompts; dry-run against a toy brief.
3. Stages 5–6 (`fanout` subcommand): worktree fan-out, verify, reconcile.
4. SKILL.md (both engines) + `openai.yaml`; render; manifest; doctor; parity test.
5. End-to-end smoke test on a small real task; tune prompts.
6. One stacked PR (`enhancement`), assignee `ExtraToast`. Capture the durable design lessons to the KB.

---

## Appendix — sources

Cross-model / critique / debate: Self-Refine (arxiv 2303.17651); LLMs cannot
self-correct reasoning without external feedback (2310.01798); multi-agent debate
(2305.14325); sycophancy / agreement collapse (2509.23055, 2509.05396);
self-preference bias (2508.06709, 2504.03846); cross-critic ensembles — N-Critics
(2310.18679), EdgeJury (2601.00850); CriticBench (2402.14809).
Orchestration: Anthropic "How we built our multi-agent research system" (90.2%,
15×, 80% variance, fan-out heuristics); Anthropic "Building effective agents"
(orchestrator-workers, evaluator-optimizer, parallelization); OpenAI "A practical
guide to building agents" (maximize single agent first, instructions are #1).
Routing: Triage SWE routing (2604.07494); morphllm router / cost (40–60%, cheap-
everywhere backfires). Consolidation: LLM-Blender (2306.02561). Failure modes:
MAST "Why Do Multi-Agent LLM Systems Fail?" (2503.13657, 14 modes, κ=0.88).
Clarification: ambiguity-not-asked (2605.25284); uncertainty-aware (2603.26233);
clarifying-questions-as-Bayesian-experimental-design (2502.04485, 2502.13069).
