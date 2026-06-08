# The `Pipeline Complete` CI contract

**Org rule:** every repo has exactly ONE CI pipeline, and it terminates in a
single aggregating job named **`Pipeline Complete`**. That job name is the ONLY
required status check in the common branch ruleset; every other job feeds it via
`needs:`. Adding/renaming an upstream job never touches branch protection — just
keep it in the aggregator's `needs:`.

personal-stack's active "Main" ruleset **already requires** the `Pipeline
Complete` check (context `Pipeline Complete`, integration_id 15368 = GitHub
Actions). No current workflow emits it, so **no PR can merge** until the
consolidated pipeline exists. This is gated on the workflows-token fix (PR #620;
see ENVIRONMENT-AND-BLOCKERS.md).

## Canonical aggregator pattern

```yaml
  pipeline-complete:
    name: Pipeline Complete
    if: always()
    needs: [lint, test, build]   # every gating job
    runs-on: ubuntu-latest
    steps:
      - name: Verify all gating jobs succeeded
        uses: re-actors/alls-green@release/v1
        with:
          jobs: ${{ toJSON(needs) }}
```

`if: always()` + `alls-green` means a skipped/failed/cancelled gating job fails
the aggregator — a green `Pipeline Complete` truly means everything passed.
Canonical template: `ExtraToast/repo-template/.github/workflows/ci.yml`.

## Consolidating personal-stack CI (issue #608)

Today these run separately:
- `fast.yml` (push, non-main) — change-detection, lint-kotlin matrix, lint/typecheck frontend, unit tests.
- `full.yml` (PR) — integration, e2e, architecture, security, contracts.
- `contract-validate.yml`, `migration-guard.yml`, `vault-bootstrap-validate.yml` (PR).
- `system-tests.yml` (workflow_call), `nightly.yml`, `build-and-publish.yml`, `prod-smoke.yml`, `crac-train.yml`.

Target: one `ci.yml` on `pull_request` (+ push to main) that runs the gating set
(keep the existing change-detection + matrix logic — fold jobs in rather than
rewrite) and ends in a `Pipeline Complete` job depending on all of them. Keep
nightly/build-publish/prod-smoke/crac-train as separate non-gating workflows.
Migrate by reference into reusable workflows from `ExtraToast/github-workflows`
once that repo exists (issue #612) — but the first cut can be in-repo.

**Note:** workflow files cannot be pushed by the automation token until PR #620
is merged + the App perms approved + the token re-minted. Until then, author the
workflow under `.github/workflows-pending/` and have a `workflow`-scoped actor
move it (the repo-template README documents the exact `git mv`).
