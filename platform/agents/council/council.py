#!/usr/bin/env python3
"""council — cross-model planning + fan-out orchestrator.

`plan` runs stages 1-4: two different model families plan a brief independently,
critique each other's plan for two rounds, then a single judge consolidates one
plan plus a parallel task DAG. `fanout` (added separately) executes that DAG.

The script is engine-agnostic — it shells out to `claude -p` and `codex exec`
and runs identically whether the host session is Claude or Codex. All state is
plain JSON/Markdown under .council/runs/<id>/ so a run is resumable and the
hand-offs between stages are structured rather than free-text.

Stdlib only. See platform/agents/council/README.md.
"""

from __future__ import annotations

import argparse
import concurrent.futures
import json
import os
import re
import subprocess
import sys
import tempfile
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable, Optional, TypeVar

HERE = Path(__file__).resolve().parent
PROMPTS_DIR = HERE / "prompts"
SCHEMAS_DIR = HERE / "schemas"


def repo_root() -> Path:
    try:
        out = subprocess.run(
            ["git", "rev-parse", "--show-toplevel"],
            capture_output=True, text=True, check=True, cwd=str(HERE),
        )
        return Path(out.stdout.strip())
    except Exception:
        return HERE.parents[2]


REPO_ROOT = repo_root()
RUNS_ROOT = REPO_ROOT / ".council" / "runs"


# --------------------------------------------------------------------------
# engines
# --------------------------------------------------------------------------

@dataclass(frozen=True)
class Engine:
    """A model behind one of the two CLIs."""
    cli: str      # "claude" | "codex"
    model: str    # alias (claude) or model id (codex)

    @property
    def label(self) -> str:
        return f"{self.cli}:{self.model}"


# Tiers. Expensive models plan / critique / judge (errors propagate there);
# cheap models do the fan-out. Overridable via CLI flags.
PLANNER_A = Engine("claude", "opus")
PLANNER_B = Engine("codex", "gpt-5.5")
CONSOLIDATOR = Engine("claude", "opus")

DEFAULT_ROUNDS = 2
CODEX_REASONING = os.environ.get("COUNCIL_CODEX_REASONING", "high")
PLAN_TIMEOUT_S = int(os.environ.get("COUNCIL_PLAN_TIMEOUT_S", "1200"))

# fan-out tier
WORKER_PERMISSION_MODE = "acceptEdits"   # auto-accept edits; no command approval
WORKER_TIMEOUT_S = int(os.environ.get("COUNCIL_WORKER_TIMEOUT_S", "1800"))
VERIFY_TIMEOUT_S = int(os.environ.get("COUNCIL_VERIFY_TIMEOUT_S", "600"))
VERIFIER = Engine("claude", "sonnet")
DEFAULT_MAX_WORKERS = 6
WT_ROOT = Path(tempfile.gettempdir()) / "council-worktrees"


@dataclass
class EngineResult:
    label: str
    text: str
    cost_usd: Optional[float] = None
    raw: Optional[dict] = None


def child_env() -> dict:
    """Environment for sub-invocations: silence the KB hooks so council's own
    prompts never get recalled into or digested out to the knowledge base."""
    env = dict(os.environ)
    env["KB_AUTO_MCP_DISABLED"] = "1"
    return env


def run_claude(prompt: str, model: str, *, cwd: Optional[Path] = None,
               permission_mode: str = "plan",
               timeout: int = PLAN_TIMEOUT_S) -> EngineResult:
    # "plan" = read-only repo access, no edits: correct for the planning tier.
    cmd = ["claude", "-p", "--model", model, "--output-format", "json",
           "--permission-mode", permission_mode]
    proc = subprocess.run(
        cmd, input=prompt, capture_output=True, text=True,
        cwd=str(cwd or REPO_ROOT), env=child_env(), timeout=timeout,
    )
    if proc.returncode != 0:
        raise RuntimeError(f"claude:{model} exited {proc.returncode}: "
                           f"{proc.stderr.strip()[:500]}")
    try:
        data = json.loads(proc.stdout)
    except json.JSONDecodeError as exc:
        raise RuntimeError(f"claude:{model} non-JSON output: {exc}: "
                           f"{proc.stdout[:300]}") from exc
    return EngineResult(
        label=f"claude:{model}",
        text=str(data.get("result", "")),
        cost_usd=data.get("total_cost_usd"),
        raw=data,
    )


def run_codex(prompt: str, model: str, *, cwd: Optional[Path] = None,
              timeout: int = PLAN_TIMEOUT_S) -> EngineResult:
    # `-o` writes only the final assistant message to a file; stdout is noisy
    # (banner + hook wrappers) so we read the message back from the file.
    last = Path(
        subprocess.run(["mktemp"], capture_output=True, text=True, check=True)
        .stdout.strip()
    )
    cmd = [
        "codex", "exec", "-m", model,
        "-c", f"model_reasoning_effort={CODEX_REASONING}",
        "-s", "read-only", "--skip-git-repo-check",
        "-o", str(last), prompt,
    ]
    try:
        proc = subprocess.run(
            cmd, capture_output=True, text=True,
            cwd=str(cwd or REPO_ROOT), env=child_env(), timeout=timeout,
        )
        if proc.returncode != 0:
            raise RuntimeError(f"codex:{model} exited {proc.returncode}: "
                               f"{proc.stderr.strip()[:500]}")
        text = last.read_text().strip()
    finally:
        last.unlink(missing_ok=True)
    return EngineResult(label=f"codex:{model}", text=text)


def run_engine(engine: Engine, prompt: str, *, cwd: Optional[Path] = None,
               timeout: int = PLAN_TIMEOUT_S, retries: int = 1) -> EngineResult:
    def once() -> EngineResult:
        if engine.cli == "claude":
            return run_claude(prompt, engine.model, cwd=cwd, timeout=timeout)
        if engine.cli == "codex":
            return run_codex(prompt, engine.model, cwd=cwd, timeout=timeout)
        raise ValueError(f"unknown cli: {engine.cli}")

    last: Exception = RuntimeError("no attempt")
    for attempt in range(retries + 1):
        try:
            return once()
        except (RuntimeError, ValueError) as exc:
            last = exc
            if attempt < retries:
                log(f"{engine.label} attempt {attempt + 1} failed ({exc}); retrying")
                time.sleep(3)
    raise last


# --------------------------------------------------------------------------
# small helpers
# --------------------------------------------------------------------------

T = TypeVar("T")


def parallel(thunks: list[Callable[[], T]]) -> list[T]:
    """Run thunks concurrently, return results in order. Raises if any raises."""
    with concurrent.futures.ThreadPoolExecutor(max_workers=len(thunks)) as ex:
        futs = [ex.submit(t) for t in thunks]
        return [f.result() for f in futs]


def render(template: str, **values: str) -> str:
    """Replace {{key}} tokens. Double braces avoid clashing with JSON braces."""
    out = template
    for key, val in values.items():
        out = out.replace("{{" + key + "}}", val)
    return out


def load_prompt(name: str) -> str:
    return (PROMPTS_DIR / f"{name}.md").read_text()


def load_schema_text(name: str) -> str:
    return json.dumps(json.loads((SCHEMAS_DIR / f"{name}.schema.json").read_text()),
                      indent=2)


def extract_json(text: str) -> dict:
    """Pull a JSON object out of a model reply, tolerating ```json fences and
    surrounding prose. Tries a clean parse first (so backticks or braces inside
    string values survive), then a string-aware scan from the first brace."""
    stripped = text.strip()
    try:
        obj = json.loads(stripped)
        if isinstance(obj, dict):
            return obj
    except json.JSONDecodeError:
        pass
    start = stripped.find("{")
    if start == -1:
        raise ValueError(f"no JSON object found in reply: {text[:200]}")
    depth = 0
    in_str = False
    esc = False
    for i in range(start, len(stripped)):
        ch = stripped[i]
        if in_str:
            if esc:
                esc = False
            elif ch == "\\":
                esc = True
            elif ch == '"':
                in_str = False
            continue
        if ch == '"':
            in_str = True
        elif ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return json.loads(stripped[start:i + 1])
    raise ValueError(f"unbalanced JSON in reply: {text[:200]}")


def plan_waves(tasks: list[dict]) -> list[list[str]]:
    """Topologically group task ids into parallel waves (Kahn's algorithm).
    Raises on unknown dependency or cycle. Pure — covered by --self-test."""
    ids = {t["id"] for t in tasks}
    deps = {t["id"]: list(t.get("depends_on", [])) for t in tasks}
    for tid, ds in deps.items():
        for d in ds:
            if d not in ids:
                raise ValueError(f"task {tid!r} depends on unknown task {d!r}")
    remaining = dict(deps)
    done: set[str] = set()
    waves: list[list[str]] = []
    while remaining:
        ready = sorted(t for t, ds in remaining.items()
                       if all(d in done for d in ds))
        if not ready:
            raise ValueError(f"dependency cycle among tasks: "
                             f"{sorted(remaining)}")
        waves.append(ready)
        for t in ready:
            done.add(t)
            del remaining[t]
    return waves


# --------------------------------------------------------------------------
# run directory / state
# --------------------------------------------------------------------------

@dataclass
class Run:
    path: Path
    costs: list[tuple[str, float]] = field(default_factory=list)

    @classmethod
    def create(cls, brief: str, slug: Optional[str]) -> "Run":
        stamp = time.strftime("%Y%m%d-%H%M%S")
        slug = slug or _slugify(brief.splitlines()[0] if brief.strip() else "run")
        path = RUNS_ROOT / f"{stamp}-{slug}"
        path.mkdir(parents=True, exist_ok=True)
        return cls(path)

    @classmethod
    def open(cls, path: Path) -> "Run":
        if not path.exists():
            raise SystemExit(f"run dir not found: {path}")
        return cls(path)

    def write_text(self, name: str, text: str) -> None:
        (self.path / name).write_text(text)

    def write_json(self, name: str, obj: object) -> None:
        (self.path / name).write_text(json.dumps(obj, indent=2))

    def read_json(self, name: str) -> dict:
        return json.loads((self.path / name).read_text())

    def has(self, name: str) -> bool:
        return (self.path / name).exists()

    def record(self, res: EngineResult) -> EngineResult:
        if res.cost_usd is not None:
            self.costs.append((res.label, res.cost_usd))
        return res

    def set_state(self, **kw: object) -> None:
        state = {}
        if self.has("state.json"):
            state = self.read_json("state.json")
        state.update(kw)
        self.write_json("state.json", state)


def _slugify(text: str) -> str:
    s = re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")
    return (s[:48] or "run")


# --------------------------------------------------------------------------
# stages 1-4
# --------------------------------------------------------------------------

def stage_plan(run: Run, brief: str, a: Engine, b: Engine) -> tuple[dict, dict]:
    if run.has("planA.v1.json") and run.has("planB.v1.json"):
        log("stage 1: dual plans already present, skipping")
        return run.read_json("planA.v1.json"), run.read_json("planB.v1.json")
    log(f"stage 1: independent plans  {a.label} ║ {b.label}")
    schema = load_schema_text("plan")
    tmpl = load_prompt("planner")

    def mk(engine: Engine) -> Callable[[], EngineResult]:
        prompt = render(tmpl, engine_label=engine.label, brief=brief,
                        repo_root=str(REPO_ROOT), schema=schema)
        return lambda: run.record(run_engine(engine, prompt))

    res_a, res_b = parallel([mk(a), mk(b)])
    plan_a, plan_b = extract_json(res_a.text), extract_json(res_b.text)
    run.write_json("planA.v1.json", plan_a)
    run.write_json("planB.v1.json", plan_b)
    return plan_a, plan_b


def stage_critique_round(run: Run, brief: str, a: Engine, b: Engine,
                         plan_a: dict, plan_b: dict, rnd: int) -> tuple[dict, dict]:
    out_a, out_b = f"planA.v{rnd + 1}.json", f"planB.v{rnd + 1}.json"
    if run.has(out_a) and run.has(out_b):
        log(f"stage 2: critique round {rnd} already present, skipping")
        return run.read_json(out_a), run.read_json(out_b)
    log(f"stage 2: cross-critique round {rnd}  ({a.label} ⇄ {b.label})")
    critic_tmpl = load_prompt("critic")
    schema = load_schema_text("plan")

    # Cross: each model critiques the OTHER's plan.
    def crit(critic: Engine, plan: dict) -> Callable[[], EngineResult]:
        prompt = render(critic_tmpl, engine_label=critic.label, brief=brief,
                        repo_root=str(REPO_ROOT),
                        plan=json.dumps(plan, indent=2))
        return lambda: run.record(run_engine(critic, prompt))

    crit_of_a, crit_of_b = parallel([crit(b, plan_a), crit(a, plan_b)])
    run.write_text(f"critique-of-A.r{rnd}.md", crit_of_a.text)
    run.write_text(f"critique-of-B.r{rnd}.md", crit_of_b.text)

    # Each author revises its own plan using the critique it received.
    rev_tmpl = load_prompt("reviser")

    def rev(author: Engine, plan: dict, critique: str) -> Callable[[], EngineResult]:
        prompt = render(rev_tmpl, engine_label=author.label, brief=brief,
                        repo_root=str(REPO_ROOT), plan=json.dumps(plan, indent=2),
                        critique=critique, schema=schema)
        return lambda: run.record(run_engine(author, prompt))

    rev_a, rev_b = parallel([rev(a, plan_a, crit_of_a.text),
                             rev(b, plan_b, crit_of_b.text)])
    next_a, next_b = extract_json(rev_a.text), extract_json(rev_b.text)
    run.write_json(out_a, next_a)
    run.write_json(out_b, next_b)
    return next_a, next_b


def stage_consolidate(run: Run, brief: str, plan_a: dict, plan_b: dict,
                      rounds: int) -> dict:
    if run.has("tasks.json") and run.has("consolidated_plan.md"):
        log("stage 4: consolidation already present, skipping")
        return run.read_json("tasks.json")
    log(f"stage 4: consolidation  ({CONSOLIDATOR.label})")
    history_parts = []
    for r in range(1, rounds + 1):
        for side in ("A", "B"):
            name = f"critique-of-{side}.r{r}.md"
            if run.has(name):
                history_parts.append(f"## Round {r} — critique of plan {side}\n"
                                     + (run.path / name).read_text())
    prompt = render(
        load_prompt("consolidator"), brief=brief, repo_root=str(REPO_ROOT),
        plan_a=json.dumps(plan_a, indent=2), plan_b=json.dumps(plan_b, indent=2),
        history="\n\n".join(history_parts) or "(no critiques recorded)",
        schema=load_schema_text("consolidated"),
    )
    res = run.record(run_engine(CONSOLIDATOR, prompt))
    obj = extract_json(res.text)
    tasks = obj.get("tasks", [])
    validate_tasks(tasks)
    run.write_text("consolidated_plan.md", obj.get("consolidated_plan_markdown", ""))
    run.write_json("tasks.json", tasks)
    return tasks


def validate_tasks(tasks: list[dict]) -> None:
    """Structural + DAG validation (we don't ship a full JSON-Schema validator;
    this checks the fields fan-out actually relies on)."""
    if not isinstance(tasks, list) or not tasks:
        raise ValueError("consolidator returned no tasks")
    required = {"id", "objective", "depends_on", "paths", "model", "verify"}
    seen: set[str] = set()
    for t in tasks:
        missing = required - t.keys()
        if missing:
            raise ValueError(f"task {t.get('id', '?')} missing fields: {sorted(missing)}")
        if t["id"] in seen:
            raise ValueError(f"duplicate task id: {t['id']}")
        seen.add(t["id"])
    plan_waves(tasks)  # raises on cycle / unknown dep


# --------------------------------------------------------------------------
# stages 5-6: fan-out + verify + reconcile
# --------------------------------------------------------------------------

def git(*args: str, cwd: Optional[Path] = None, check: bool = True,
        timeout: int = 120) -> subprocess.CompletedProcess:
    return subprocess.run(["git", *args], cwd=str(cwd or REPO_ROOT),
                          capture_output=True, text=True, check=check,
                          timeout=timeout)


def parallel_bounded(thunks: list[Callable[[], T]], cap: int) -> list[T]:
    """Run thunks with at most `cap` concurrent, preserving order. Thunks must
    not raise (wrap failures into return values)."""
    with concurrent.futures.ThreadPoolExecutor(max_workers=max(1, cap)) as ex:
        return list(ex.map(lambda t: t(), thunks))


def run_verify(cmd: str, cwd: Path) -> tuple[Optional[int], str]:
    if not cmd.strip():
        return None, "(no verify command)"
    try:
        proc = subprocess.run(["bash", "-lc", cmd], cwd=str(cwd),
                              capture_output=True, text=True, env=child_env(),
                              timeout=VERIFY_TIMEOUT_S)
        return proc.returncode, (proc.stdout + proc.stderr)[-8000:]
    except subprocess.TimeoutExpired:
        return 124, f"(verify timed out after {VERIFY_TIMEOUT_S}s)"


def run_verifier(run: Run, task: dict, diff: str, verify_cmd: str,
                 verify_rc: Optional[int], verify_out: str) -> Optional[dict]:
    prompt = render(
        load_prompt("verifier"), objective=task.get("objective", ""),
        output_format=task.get("output_format", ""),
        paths="\n".join(f"- {p}" for p in task.get("paths", [])) or "(none)",
        diff=diff[:16000] or "(no changes)", verify_cmd=verify_cmd or "(none)",
        verify_rc=str(verify_rc), verify_output=verify_out[:6000] or "(none)",
        schema=load_schema_text("verdict"),
    )
    try:
        res = run.record(run_engine(VERIFIER, prompt, retries=0))
        return extract_json(res.text)
    except Exception as exc:  # verifier is advisory; never fail the run on it
        log(f"verifier for {task['id']} errored: {exc}")
        return None


def run_worker(run: Run, task: dict, base_ref: str, run_name: str) -> dict:
    tid = task["id"]
    paths = list(task.get("paths", []))
    branch = f"council/{run_name}/{tid}"
    wt = WT_ROOT / run_name / tid
    result: dict = {"task_id": tid, "title": task.get("title", tid),
                    "model": task.get("model"), "branch": branch,
                    "worktree": str(wt), "committed": False}
    try:
        wt.parent.mkdir(parents=True, exist_ok=True)
        git("worktree", "remove", "--force", str(wt), check=False)
        git("branch", "-D", branch, check=False)
        git("worktree", "add", "--force", "-b", branch, str(wt), base_ref)

        if paths:
            prompt = render(
                load_prompt("worker"), title=task.get("title", tid),
                objective=task["objective"],
                paths="\n".join(f"- {p}" for p in paths),
                boundaries=task.get("boundaries", ""),
                output_format=task.get("output_format", ""), cwd=str(wt))
            res = run.record(run_claude(prompt, task["model"], cwd=wt,
                                        permission_mode=WORKER_PERMISSION_MODE,
                                        timeout=WORKER_TIMEOUT_S))
            result["summary"] = res.text[-2000:]
        else:
            result["summary"] = "(verify-only task: no files to edit)"

        git("add", "-A", cwd=wt)
        dirty = git("status", "--porcelain", cwd=wt).stdout.strip()
        if dirty:
            git("-c", "user.name=council", "-c", "user.email=council@local",
                "commit", "-q", "-m", f"council: {tid}", cwd=wt)
            result["committed"] = True
            result["files_changed"] = git(
                "diff", "--name-only", f"{base_ref}..HEAD", cwd=wt
            ).stdout.split()
            diff = git("diff", f"{base_ref}..HEAD", cwd=wt, timeout=120).stdout
        else:
            result["files_changed"] = []
            diff = ""

        out_of_bounds = [f for f in result["files_changed"] if f not in paths]
        result["out_of_bounds"] = out_of_bounds

        verify_cmd = task.get("verify", "")
        rc, out = run_verify(verify_cmd, wt)
        result["verify_rc"] = rc
        result["verify_output"] = out[-4000:]

        verdict = run_verifier(run, task, diff, verify_cmd, rc, out)
        result["verdict"] = verdict

        if out_of_bounds:
            result["status"] = "out-of-bounds"
        elif paths and not result["committed"]:
            result["status"] = "no-op"
        elif rc not in (None, 0):
            result["status"] = "verify-failed"
        elif verdict is not None and not verdict.get("satisfied", True):
            result["status"] = "rejected"
        else:
            result["status"] = "ok"
    except Exception as exc:
        result["status"] = "error"
        result["error"] = str(exc)[:500]
    finally:
        wdir = run.path / "workers" / tid
        wdir.mkdir(parents=True, exist_ok=True)
        (wdir / "result.json").write_text(json.dumps(result, indent=2))
    return result


def cmd_fanout(args: argparse.Namespace) -> int:
    run = Run.open(Path(args.run))
    if not run.has("tasks.json"):
        raise SystemExit(f"no tasks.json in {run.path}; run `plan` first")
    tasks = run.read_json("tasks.json")
    by_id = {t["id"]: t for t in tasks}
    waves = plan_waves(tasks)
    cores = max(1, (os.cpu_count() or 3) - 2)
    cap = min(args.max_workers, cores)

    if args.estimate:
        print(f"council fanout — {len(tasks)} tasks in {len(waves)} wave(s), "
              f"concurrency {cap}")
        for i, wave in enumerate(waves, 1):
            models = ", ".join(f"{t}:{by_id[t].get('model')}" for t in wave)
            print(f"  wave {i}: {models}")
        print("Each task spawns one worker (cheap model) + one verifier "
              "(claude:sonnet). Worktrees are isolated; nothing is merged into "
              "your branch — results land on an integration branch for review.")
        return 0

    run_name = run.path.name
    base = git("rev-parse", "HEAD").stdout.strip()
    integ_branch = f"council/{run_name}/integration"
    integ_wt = WT_ROOT / run_name / "_integration"
    integ_wt.parent.mkdir(parents=True, exist_ok=True)
    git("worktree", "remove", "--force", str(integ_wt), check=False)
    git("branch", "-D", integ_branch, check=False)
    git("worktree", "add", "--force", "-b", integ_branch, str(integ_wt), base)
    log(f"fanout: {len(tasks)} task(s) in {len(waves)} wave(s); base {base[:8]}; "
        f"integration branch {integ_branch}; concurrency {cap}")

    results: dict[str, dict] = {}
    for wi, wave in enumerate(waves, 1):
        wave_base = git("rev-parse", "HEAD", cwd=integ_wt).stdout.strip()
        log(f"wave {wi}/{len(waves)}: {wave}  (base {wave_base[:8]})")
        thunks = [(lambda t=t: run_worker(run, by_id[t], wave_base, run_name))
                  for t in wave]
        for tid, res in zip(wave, parallel_bounded(thunks, cap)):
            results[tid] = res
            log(f"  [{tid}] {res['status']}"
                + (f" ({len(res.get('files_changed', []))} files)"
                   if res.get("committed") else ""))
        # reconcile this wave into the integration branch, in order
        for tid in wave:
            res = results[tid]
            if not res.get("committed"):
                res["merge"] = "nothing-to-merge"
                continue
            m = git("merge", "--no-ff", "-m", f"council merge {tid}",
                    f"council/{run_name}/{tid}", cwd=integ_wt, check=False)
            if m.returncode != 0:
                git("merge", "--abort", cwd=integ_wt, check=False)
                res["merge"] = "conflict"
                log(f"  [{tid}] merge CONFLICT — left out of integration")
            else:
                res["merge"] = "ok"

    if not args.keep_worktrees:
        for tid in by_id:
            git("worktree", "remove", "--force", str(WT_ROOT / run_name / tid),
                check=False)

    report = build_report(run, integ_branch, str(integ_wt), waves, results)
    run.write_json("report.json", report)
    run.write_text("report.md", render_report_md(report))
    run.set_state(stage="fanned-out", integration_branch=integ_branch)
    s = report["summary"]
    log(f"done: {s['ok']}/{s['total']} ok, {s['failed']} failed, "
        f"{s['merged']} merged into {integ_branch}")
    print(integ_branch)  # stdout: integration branch for the host to surface
    return 0


def build_report(run: Run, integ_branch: str, integ_wt: str,
                 waves: list[list[str]], results: dict[str, dict]) -> dict:
    rows = []
    ok = failed = merged = 0
    for tid, r in results.items():
        good = r.get("status") == "ok" and r.get("merge") in ("ok", None)
        ok += 1 if r.get("status") == "ok" else 0
        failed += 0 if r.get("status") == "ok" else 1
        merged += 1 if r.get("merge") == "ok" else 0
        rows.append({
            "task_id": tid, "status": r.get("status"),
            "merge": r.get("merge"), "model": r.get("model"),
            "files_changed": r.get("files_changed", []),
            "verify_rc": r.get("verify_rc"),
            "verifier_satisfied": (r.get("verdict") or {}).get("satisfied"),
            "out_of_bounds": r.get("out_of_bounds", []),
            "branch": r.get("branch"), "good": good,
        })
    return {
        "run": run.path.name, "integration_branch": integ_branch,
        "integration_worktree": integ_wt, "waves": waves, "tasks": rows,
        "summary": {"total": len(results), "ok": ok, "failed": failed,
                    "merged": merged},
    }


def render_report_md(report: dict) -> str:
    s = report["summary"]
    lines = [f"# council fan-out report — {report['run']}", "",
             f"- integration branch: `{report['integration_branch']}`",
             f"- worktree: `{report['integration_worktree']}`",
             f"- result: **{s['ok']}/{s['total']} ok**, {s['failed']} failed, "
             f"{s['merged']} merged", "", "## Tasks", "",
             "| task | status | merge | model | files | verify | verifier |",
             "|---|---|---|---|---|---|---|"]
    for t in report["tasks"]:
        lines.append(
            f"| {t['task_id']} | {t['status']} | {t['merge']} | {t['model']} "
            f"| {len(t['files_changed'])} | "
            f"{'-' if t['verify_rc'] is None else t['verify_rc']} "
            f"| {t['verifier_satisfied']} |")
    failures = [t for t in report["tasks"] if not t["good"]]
    if failures:
        lines += ["", "## Needs attention", ""]
        for t in failures:
            note = t["status"]
            if t["merge"] == "conflict":
                note += " + merge conflict"
            if t["out_of_bounds"]:
                note += f" (touched out-of-bounds: {t['out_of_bounds']})"
            lines.append(f"- `{t['task_id']}`: {note}")
    lines += ["", f"Review: `git -C {report['integration_worktree']} log --oneline`"
              f" or `git checkout {report['integration_branch']}`."]
    return "\n".join(lines) + "\n"


# --------------------------------------------------------------------------
# commands
# --------------------------------------------------------------------------

def log(msg: str) -> None:
    print(f"[council] {msg}", file=sys.stderr, flush=True)


def read_brief(arg: str) -> str:
    if arg == "-":
        return sys.stdin.read()
    p = Path(arg)
    if not p.exists():
        raise SystemExit(f"brief file not found: {arg}")
    return p.read_text()


def cmd_plan(args: argparse.Namespace) -> int:
    a = parse_engine(args.planner_a, PLANNER_A)
    b = parse_engine(args.planner_b, PLANNER_B)
    rounds = args.rounds

    if args.estimate:
        calls = 2 + rounds * 4 + 1
        print(f"council plan — estimated model calls: {calls}")
        print(f"  stage 1 dual plans      : 2  ({a.label}, {b.label})")
        print(f"  stage 2 critique+revise : {rounds * 4}  ({rounds} rounds x "
              f"[2 critiques + 2 revisions])")
        print(f"  stage 4 consolidation   : 1  ({CONSOLIDATOR.label})")
        print("These are expensive-tier calls; fan-out (cheap workers) is "
              "separate. Multi-agent runs ~15x the tokens of a single chat — "
              "use council only for large, decomposable work.")
        return 0

    brief = read_brief(args.brief)
    run = Run.open(Path(args.run)) if args.run else Run.create(brief, args.slug)
    run.write_text("brief.md", brief)
    run.set_state(stage="plan", rounds=rounds, planner_a=a.label,
                  planner_b=b.label)
    log(f"run dir: {run.path}")

    plan_a, plan_b = stage_plan(run, brief, a, b)
    for rnd in range(1, rounds + 1):
        plan_a, plan_b = stage_critique_round(run, brief, a, b, plan_a, plan_b, rnd)
    tasks = stage_consolidate(run, brief, plan_a, plan_b, rounds)
    run.set_state(stage="planned", task_count=len(tasks))

    waves = plan_waves(tasks)
    total = sum(c for _, c in run.costs)
    log(f"done: {len(tasks)} tasks in {len(waves)} wave(s); "
        f"recorded claude cost ${total:.2f} (codex cost not reported by CLI)")
    print(str(run.path))  # stdout: the run dir, for the host to pick up
    return 0


def parse_engine(spec: Optional[str], default: Engine) -> Engine:
    """spec form: "cli:model" e.g. "claude:opus" or "codex:gpt-5.5"."""
    if not spec:
        return default
    if ":" not in spec:
        raise SystemExit(f"engine must be cli:model, got {spec!r}")
    cli, model = spec.split(":", 1)
    if cli not in ("claude", "codex"):
        raise SystemExit(f"engine cli must be claude|codex, got {cli!r}")
    return Engine(cli, model)


def cmd_self_test(_args: argparse.Namespace) -> int:
    failures: list[str] = []

    def check(name: str, cond: bool) -> None:
        if not cond:
            failures.append(name)
        print(f"  {'ok  ' if cond else 'FAIL'} {name}")

    # plan_waves
    tasks = [
        {"id": "a", "depends_on": []},
        {"id": "b", "depends_on": ["a"]},
        {"id": "c", "depends_on": ["a"]},
        {"id": "d", "depends_on": ["b", "c"]},
    ]
    check("plan_waves groups by dependency",
          plan_waves(tasks) == [["a"], ["b", "c"], ["d"]])
    check("plan_waves rejects cycle", _raises(
        lambda: plan_waves([{"id": "x", "depends_on": ["y"]},
                            {"id": "y", "depends_on": ["x"]}])))
    check("plan_waves rejects unknown dep", _raises(
        lambda: plan_waves([{"id": "x", "depends_on": ["nope"]}])))

    # extract_json
    check("extract_json plain", extract_json('{"a": 1}') == {"a": 1})
    check("extract_json fenced",
          extract_json('text\n```json\n{"a": [1,2]}\n```\nmore') == {"a": [1, 2]})
    check("extract_json with braces in string",
          extract_json('{"k": "a{b}c"}') == {"k": "a{b}c"})
    check("extract_json keeps code fences inside string value",
          extract_json('{"md": "see ```bash\\nx\\n``` end", "n": 1}')
          == {"md": "see ```bash\nx\n``` end", "n": 1})
    check("extract_json outer fence with inner fences",
          extract_json('```json\n{"md": "a ```inner``` b"}\n```')
          == {"md": "a ```inner``` b"})
    check("extract_json none raises", _raises(lambda: extract_json("no json here")))

    # render
    check("render replaces tokens",
          render("hi {{name}} {{name}}", name="x") == "hi x x")
    check("render leaves JSON braces",
          render('{"x": {{v}}}', v="1") == '{"x": 1}')

    # validate_tasks
    check("validate_tasks rejects empty", _raises(lambda: validate_tasks([])))
    check("validate_tasks rejects missing fields",
          _raises(lambda: validate_tasks([{"id": "a"}])))

    print(f"\n{'PASS' if not failures else 'FAIL: ' + ', '.join(failures)}")
    return 1 if failures else 0


def _raises(fn: Callable[[], object]) -> bool:
    try:
        fn()
        return False
    except Exception:
        return True


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="council", description=__doc__)
    p.add_argument("--self-test", action="store_true",
                   help="run pure-function checks (no model calls) and exit")
    sub = p.add_subparsers(dest="command")

    pl = sub.add_parser("plan", help="stages 1-4: dual plans, critique, consolidate")
    pl.add_argument("--brief", help="brief file path, or - for stdin")
    pl.add_argument("--run", help="existing run dir to resume")
    pl.add_argument("--slug", help="slug for the run dir name")
    pl.add_argument("--rounds", type=int, default=DEFAULT_ROUNDS,
                    help=f"critique rounds (default {DEFAULT_ROUNDS})")
    pl.add_argument("--planner-a", help="override, form cli:model")
    pl.add_argument("--planner-b", help="override, form cli:model")
    pl.add_argument("--estimate", action="store_true",
                    help="print planned call count and exit without spending")
    pl.set_defaults(func=cmd_plan)

    fo = sub.add_parser("fanout", help="stages 5-6: execute the task DAG, "
                                       "verify, reconcile onto a branch")
    fo.add_argument("--run", required=True, help="run dir with a tasks.json")
    fo.add_argument("--max-workers", type=int, default=DEFAULT_MAX_WORKERS,
                    help=f"max concurrent workers (default {DEFAULT_MAX_WORKERS}, "
                         "clamped to cores-2)")
    fo.add_argument("--keep-worktrees", action="store_true",
                    help="keep per-task worktrees for inspection")
    fo.add_argument("--estimate", action="store_true",
                    help="print the wave/worker plan and exit without spending")
    fo.set_defaults(func=cmd_fanout)
    return p


def main(argv: Optional[list[str]] = None) -> int:
    args = build_parser().parse_args(argv)
    if args.self_test:
        return cmd_self_test(args)
    if not getattr(args, "command", None):
        build_parser().print_help()
        return 2
    if args.command == "plan" and not args.estimate and not args.brief:
        raise SystemExit("plan requires --brief (or --brief -)")
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
