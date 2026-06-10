# Feature Specification: Fix Stalwart published MX + catch-all account identity

**Feature Branch**: `003-stalwart-mx-and-account-fix`
**Created**: 2026-06-10
**Status**: Draft
**Input**: Operator report: Stalwart manages the Cloudflare DNS itself and the mailbox can
send, yet mail to `joris.jonkers@jorisjonkers.dev` is accepted-but-not-received (no bounce).
The account's name should be "Joris Jonkers" and its primary address `joris.jonkers@`, with
`extratoast@` as an additional address.

## Context

Live investigation (DNS-over-HTTPS + the `stalwart-apply` reconcile log) found two defects
that spec 002 did not address because it reasoned from the repo rather than the running
server:

1. **Published MX target is the pod name.** `dig MX jorisjonkers.dev` returns
   `10 stalwart-78587fc555-dfdr5.` — a Kubernetes pod name. Stalwart owns the zone
   (`dnsManagement: Automatic` + Cloudflare token) and publishes the MX from its
   `server.hostname` setting, which it seeds from the container OS hostname (the pod name)
   at first boot and persists. `infra/stalwart/apply.sh` only ever set `defaultHostname`,
   never `server.hostname`, so the MX is a non-resolvable single-label name that also rots
   on every pod roll. Inbound mail from non-Microsoft senders cannot resolve the server.

2. **Catch-all target is not a deliverable address.** `STALWART_CATCHALL` is
   `joris.jonkers@jorisjonkers.dev`, but the live operator mailbox is a manually-created
   account whose primary address is `extratoast@jorisjonkers.dev`; `joris.jonkers@` is
   neither its primary nor an alias, and the declarative `joris.jonkers` account is skipped
   (no Vault password). So even mail that reaches Stalwart has no destination for the
   catch-all.

A latent Microsoft 365 factor remains (the domain is still an accepted domain in an M365
tenant, so Microsoft-origin senders route internally); it is operator-resolved and
documented in the runbook, not in scope for code.

## Requirements *(mandatory)*

- **FR-001**: The reconcile MUST pin `server.hostname` to `mail.jorisjonkers.dev`
  (`STALWART_HOSTNAME`) so the auto-managed DNS publishes `MX 10 mail.jorisjonkers.dev` and
  the value survives pod rolls. Implemented as an idempotent `Bootstrap.serverHostname`
  update; tolerant (warns, does not abort the reconcile) if the management field moves.
- **FR-002**: The catch-all target account MUST be declared with primary
  `joris.jonkers@jorisjonkers.dev`, full name "Joris Jonkers" (the account `description`),
  and `extratoast@jorisjonkers.dev` as an alias. `accounts.json` gains an optional
  `displayName`; `apply.sh` maps it to the account `description` only when declared (never
  clears a webadmin-set value otherwise).
- **FR-003**: The offline test harness MUST assert the reconcile pins `serverHostname`, maps
  `displayName` → `description`, and that the target account carries the full name.
- **FR-004**: No change may break the working reconcile of existing accounts; renaming the
  live `extratoast@`-primary mailbox is operator-executed (webadmin) and documented, because
  the reconcile locates accounts by primary address and the live mailbox cannot be safely
  renamed remotely.

## Success Criteria *(mandatory)*

- **SC-001**: After the next reconcile, `dig MX jorisjonkers.dev` → `10 mail.jorisjonkers.dev`.
- **SC-002**: A Gmail-origin message to `joris.jonkers@jorisjonkers.dev` is delivered to the
  operator mailbox; an unknown local recipient is delivered there via catch-all.
- **SC-003**: The operator account shows full name "Joris Jonkers", primary `joris.jonkers@`,
  alias `extratoast@`.

## Scope

**In scope**: the `server.hostname` pin, declarative `displayName` support and the target
account definition, regression tests, runbook correction.

**Out of scope (operator-executed)**: setting Vault `joris.password`, the webadmin account
rename (mail-preserving), and removing the domain from the Microsoft 365 tenant.
