# Quickstart: Scheduled KB Curation Passes

## Tier-1 daily triage (dry-run, shipped)

Trigger on demand and read the proposed triage from logs:

```sh
kubectl -n agents-system create job --from=cronjob/agents-kb-curator-triage kb-curator-now
kubectl -n agents-system logs -f job/kb-curator-now
```

Expect a markdown report (KEEP/DISCARD/DUP/UNSURE) ending in "DRY RUN — no changes applied" and zero KB writes.

## Promote Tier-1 to apply-mode

Once the dry-run logs look right, set `KB_CURATOR_APPLY=1` on the CronJob (the manifest also extends `--allowed-tools` with `mcp__knowledge__knowledge_ingest_note` when applying). Verify:

- unscoped durable notes gain a scope + ≤3 tags;
- low-signal notes gain `_discard-candidate` (not deleted);
- a same-day re-run is a no-op (idempotency stamp).

## Tier-2 weekly consolidation

Trigger on demand:

```sh
kubectl -n agents-system create job --from=cronjob/agents-kb-curator-weekly kb-curator-weekly-now
kubectl -n agents-system logs -f job/kb-curator-weekly-now
```

Expect the Job to push a `curator/weekly-<date>` branch to `ExtraToast/knowledge-vault` and print a compare URL:
`https://github.com/ExtraToast/knowledge-vault/compare/main...curator/weekly-<date>?expand=1`
Open the PR from that URL, review the staged merges/renames/rollups/archive-candidates + conflict digest, and merge manually. The Job never auto-merges and never deletes KB notes directly.

## Pre-merge validation (no cluster required)

```sh
kustomize build platform/cluster/flux/apps/agents | grep -c agents-kb-curator
# extract + bash -n the embedded CronJob scripts; validate the generated MCP config is valid JSON
```

## Live verification checklist

- Tier-1 dry-run: zero writes, within deadline/turn caps.
- Tier-1 apply: additive-only, idempotent re-run.
- Tier-2: exactly one branch/PR (or none), no auto-merge, no KB deletes; deploy-key VSS synced (R1) — confirm `kubectl -n agents-system get secret agents-knowledge-vault-deploy-key`.
