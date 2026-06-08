# Agent Spec-Driven Development

This is the end-to-end path for creating a spec-driven feature with council.

## Create a feature

1. Start from the target repository root and make sure `.specify` exists. For
   personal-stack it is committed; for other projects, install the kit with:

   ```bash
   curl -fsSL -H "Authorization: Bearer ${KB_BEARER_TOKEN}" \
     "${KB_URL}/install.sh" | AGENT_KIT_PROJECT_ROOT="$PWD" \
     bash -s -- --agent all --scope project
   ```

2. Create or refine the feature spec with the dot-form Spec Kit commands:
   `/speckit.specify`, `/speckit.clarify`, `/speckit.plan`,
   `/speckit.tasks`, and `/speckit.analyze`. User-scope installs also work; if
   `.specify` is absent, `/speckit.specify` scaffolds a minimal project runtime
   before creating the first spec.

3. Run council planning against the spec or a brief:

   ```bash
   python3 platform/agents/council/council.py plan \
     --brief specs/001-feature/spec.md \
     --spec-dir specs/001-feature
   ```

   `--brief` may also be `-` for stdin or a free-text string. Without
   `--spec-dir`, council allocates the next `specs/NNN-slug` directory and
   writes `spec.md`, `plan.md`, and generated `tasks.md` under the run
   directory.

4. Review `consolidated_plan.md`, `tasks.json`, and
   `specs/NNN-slug/tasks.md` in the run directory. `tasks.json` is canonical;
   `tasks.md` is generated from it and must keep an exact JSON-block bijection.
   If a human changes task data in `tasks.md`, reconcile by regenerating with
   `council plan --run <run> --brief <run>/brief.md` or by making the matching
   `tasks.json` change before fan-out.

5. Execute the approved DAG:

   ```bash
   python3 platform/agents/council/council.py fanout --run .council/runs/<id>
   ```

   Fan-out commits `specs/NNN-slug` onto the integration branch before workers
   are spawned. Workers then run in isolated worktrees and the orchestrator
   merges successful task commits onto `council/<run>/integration` for review.

## Gates and scope

The analyze gate hard-fails before execution when
`.specify/memory/constitution.md` is missing or placeholder-like, when
`tasks.md` is missing beside `tasks.json`, or when `tasks.md` no longer
round-trips to the canonical task JSON. Constitution text is injected into
planner, critic, reviser, and consolidator prompts only; worker and verifier
prompts do not receive it.

The MCP ConfigMap is explicitly unchanged by SDD. Do not edit
`platform/cluster/flux/apps/agents/mcp/agents-mcp-servers-configmap.yaml` for
Spec Kit command, scaffold, or council task changes.

The commit-capture and stop-digest hooks are not path-suppressible. The edit
recall hook has an allowlist, but commit capture and stop digest can only be
disabled for a session through the global hook controls such as
`KB_AUTO_MCP_DISABLED=1`.

## Upstream pin and upgrades

The vendored scaffold is tracked in
`platform/agents/kit/spec-kit-source.lock`: upstream
`https://github.com/github/spec-kit`, tag `v0.9.5`, with the resolved commit
currently recorded as `UNRESOLVED-DNS-BLOCKED`. The agent-kit owner must replace
that placeholder with the real tag commit before merge.

Spec Kit self-upgrades are human-only and off-runtime. Do not run them from
installer, runner, hook, or council execution paths. The owner workflow is:

1. Install or upgrade `specify-cli` pinned to the candidate upstream tag.
2. Run `specify self upgrade --dry-run` outside runtime installs and compare
   the scaffold changes.
3. Resolve the candidate tag commit and update `spec-kit-source.lock`.
4. Copy only the approved `.specify` scaffold paths into
   `platform/agents/kit/templates/repo/.specify/`.
5. Keep `.specify/memory/constitution.md` as the committed personal-stack file;
   do not replace it with the generic upstream template.
