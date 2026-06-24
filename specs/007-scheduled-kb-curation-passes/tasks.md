# Tasks: Scheduled KB Curation Passes

Feature dir: `specs/007-scheduled-kb-curation-passes/` · Branch: `007-scheduled-kb-curation-passes`
Stacked on the Tier-1 dry-run PR (#724, branch `knowledge/kb-curation-daily-triage`).

`[P]` = parallel-safe (different files, no ordering dependency).

## US1 — Daily triage dry-run (P1) — SHIPPED

- [x] **T001** Tier-1 daily triage CronJob (dry-run, Haiku, read-only, capped) — `platform/cluster/flux/apps/agents/kb-curator/cronjob.yaml` + kustomization wiring. Delivered in PR #724.

## US2 — Promote Tier-1 to apply-mode (P1)

- [ ] **T002** Enhance `kb-curator/cronjob.yaml` so `KB_CURATOR_APPLY=1` switches the run to apply-mode: extend `--allowed-tools` with `mcp__knowledge__knowledge_ingest_note`, and branch the prompt to apply additive metadata (scope, ≤3 tags, confidence) + reversible `_*-candidate` tags + the `curated:triage:<date>` idempotency stamp. Dry-run (`0`) stays read-only/zero-write. (FR-004, FR-005, FR-006)
- [ ] **T003** Validate T002: `kustomize build` + extract & `bash -n` the embedded script + MCP-config JSON validity. Confirm dry-run path still grants no write tools.

## US3 — Weekly consolidation PR (P2)

- [ ] **T004 [P]** New `credentials/knowledge-vault-deploy-key-vss.yaml` — `VaultStaticSecret` `agents-knowledge-vault-deploy-key` in `agents-system` reading Vault `secret/knowledge-system/vault-deploy-key` (fields `private_key`, `known_hosts`); add to `credentials/kustomization.yaml`. (R1: confirm VSO Vault policy permits the path; else mirror to an `agents/` path.) (FR-010)
- [ ] **T005** New `kb-curator/weekly-consolidation.yaml` — Tier-2 weekly CronJob (`agents-kb-curator-weekly`, `0 5 * * 1`, Sonnet): agent-runner image, `claude-credentials` PVC, `agents-kb-bearer`, the deploy-key secret; fresh SSH clone of `git@github.com:ExtraToast/knowledge-vault.git`; allowed-tools `list_recent,recall,find_conflicts,relations,get_note`; caps (`--max-turns ~60`, ≤40 notes, `activeDeadlineSeconds 1800`); stage merges/renames/rollups/archive-candidates + conflict digest on `curator/weekly-<date>`, push, emit compare URL; never auto-merge, never KB delete. (FR-002,003,005,007,008,009,011)
- [ ] **T006** Wire `weekly-consolidation.yaml` into `kb-curator/kustomization.yaml`.
- [ ] **T007** Validate US3: `kustomize build platform/cluster/flux/apps/agents` succeeds and includes `agents-kb-curator-weekly`; `bash -n` the embedded script; MCP-config JSON valid; deploy-key secret reference resolves in the build.

## Cross-cutting / polish

- [ ] **T008 [P]** Constitution fix (R4): remove `Co-Authored-By`/generated-by text from PRs #723 and #724 (amend commits + edit PR bodies). Ensure all 007 commits carry no attribution.
- [ ] **T009** Live verification (post-merge, requires cluster): run quickstart triggers for Tier-1 (dry-run + apply) and Tier-2; confirm SC-001..SC-005 and that the deploy-key VSS synced.

## Notes

- US1 is independently shipped; US2 and US3 are independently testable via `kustomize build`/`bash -n`.
- T009 cannot run from a workstation without cluster access; it is the acceptance gate after merge.
