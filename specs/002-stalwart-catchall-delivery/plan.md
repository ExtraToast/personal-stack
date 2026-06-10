# Implementation Plan: End-to-End Catch-All Delivery for jorisjonkers.dev

**Branch**: `002-stalwart-catchall-delivery` | **Spec**: [spec.md](./spec.md)

## Summary

The reported failure is an upstream routing problem (Microsoft 365 intercepts mail before
Stalwart), remediated operationally via the [cutover runbook](../../docs/runbooks/jorisjonkers-dev-mail-cutover.md).
The in-repo work closes the latent gap that would make the catch-all fail even after the
cutover: the catch-all **target** mailbox is not declaratively provisioned. We add it to
the existing Vault-backed reconcile, with an optional password (safe no-op when unset), an
alias for `extratoast`, and an offline test harness asserting catch-all/account consistency.

## Technical Context

- **Reconcile**: `infra/stalwart/apply.sh` (POSIX sh) reconciles `accounts.json` entries.
  Password-only entries set credentials; entries with `aliases` set aliases; an absent
  `passwordEnv` value causes the entry to be skipped (never clears existing state).
- **Catch-all**: `STALWART_CATCHALL=joris.jonkers@jorisjonkers.dev` in
  `platform/cluster/flux/apps/mail/stalwart/deployment.yaml`, applied to
  `Domain.catchAllAddress`.
- **Secrets**: `platform/cluster/flux/apps/mail/stalwart/vault-static-secrets.yaml`
  (`stalwart-mail` VaultStaticSecret) templates mailbox passwords from `secret/platform/mail`.
- **DNS**: manual in Cloudflare; `external-dns` does not manage mail records. No in-repo
  lever for MX — hence the runbook.
- **Test tooling**: `jq` available; no `bats`. Use POSIX `sh` + `jq`, no network.

## Constraints / Safety

- Optional password mirrors `account.password`/`n8n.password` so an unpopulated Vault key is
  a no-op and never resets an existing manual mailbox.
- Partial Domain/account updates only — never delete/recreate mailboxes.
- CI must stay green (flux render / kustomize build).

## Approach

1. Declare `joris.jonkers` in `accounts.json` with `passwordEnv: JORIS_MAIL_PASSWORD` and
   `aliases: ["extratoast"]`.
2. Wire `JORIS_MAIL_PASSWORD` as an **optional** `secretKeyRef` env in `deployment.yaml`,
   and add a `JORIS_MAIL_PASSWORD` template (from `joris.password`) to the `stalwart-mail`
   VaultStaticSecret.
3. Add `tests/stalwart/` POSIX-sh + jq harness validating FR-005/FR-006, runnable via a
   single entrypoint and wired into CI (platform path filter).
4. Verify locally, open PR, merge.

## Implementation engine

Implementation (steps 1–3) delegated to **codex (`gpt-5.5`, reasoning effort `medium`)** in
a `workspace-write` sandbox; orchestration, spec/runbook authoring, verification, and the
PR/merge performed by the session.
