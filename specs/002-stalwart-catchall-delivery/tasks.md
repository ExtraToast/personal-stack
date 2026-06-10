# Tasks: End-to-End Catch-All Delivery for jorisjonkers.dev

**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

## Phase 1 — Operational remediation (out-of-repo, documented)

- [x] T001 Author the DNS + M365 cutover runbook with the decisive diagnostic and a
      verification checklist — `docs/runbooks/jorisjonkers-dev-mail-cutover.md`. (FR-001)

## Phase 2 — Provision the catch-all target mailbox (codex)

- [ ] T002 Declare `joris.jonkers` in `infra/stalwart/accounts.json` with
      `passwordEnv: JORIS_MAIL_PASSWORD` and `aliases: ["extratoast"]`. (FR-002, FR-003)
- [ ] T003 Add optional `JORIS_MAIL_PASSWORD` `secretKeyRef` env (key `JORIS_MAIL_PASSWORD`,
      `optional: true`) to the `stalwart-apply` container in
      `platform/cluster/flux/apps/mail/stalwart/deployment.yaml`. (FR-004)
- [ ] T004 Add the `JORIS_MAIL_PASSWORD` template (from `joris.password`) to the
      `stalwart-mail` VaultStaticSecret in
      `platform/cluster/flux/apps/mail/stalwart/vault-static-secrets.yaml`. (FR-004)

## Phase 3 — Tests (codex)

- [ ] T005 `tests/stalwart/catchall_consistency_test.sh` (POSIX sh + jq, no network):
  - `accounts.json` is valid JSON; each entry has a `localPart`.
  - `STALWART_CATCHALL`'s local part (parsed from `deployment.yaml`) is backed by an account
    or an alias in `accounts.json`. (FR-005, SC-004)
  - The alias and credentials JSON produced by the `apply.sh` jq transforms matches the
    expected shape for a sample entry. (FR-006)
  - `JORIS_MAIL_PASSWORD` is wired in both `deployment.yaml` (optional env) and
    `vault-static-secrets.yaml` (template). (FR-006)
- [ ] T006 Provide a single runnable entrypoint (`tests/stalwart/run.sh`) and wire it into
      CI under the platform/infra path filter. (FR-007)

## Phase 4 — Ship

- [ ] T007 Run the test harness + YAML/JSON validation locally; confirm green.
- [ ] T008 Open PR on `ExtraToast/personal-stack` and merge once checks pass.
