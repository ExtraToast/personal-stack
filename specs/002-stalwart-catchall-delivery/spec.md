# Feature Specification: End-to-End Catch-All Delivery for jorisjonkers.dev

**Feature Branch**: `002-stalwart-catchall-delivery`
**Created**: 2026-06-10
**Status**: Draft
**Input**: User report: "The catch-all email address set up by Stalwart is not working.
Mail to `something@jorisjonkers.dev` bounces; I want all mail that doesn't belong to a
specific account to be received by the operator mailbox (`joris.jonkers@jorisjonkers.dev`)."

## Context

Spec [`001-declaratively-manage-stalwart-per-domain`](../001-declaratively-manage-stalwart-per-domain/spec.md)
made the Stalwart per-domain catch-all (`Domain.catchAllAddress`) declarative: the
`stalwart-apply` sidecar (`infra/stalwart/apply.sh`) reconciles `STALWART_CATCHALL`
(`joris.jonkers@jorisjonkers.dev`) onto the domain on every pod boot. That piece is
correct and is **not** the cause of the reported failure.

The reported bounce is conclusive about where delivery actually breaks:

```
Generating server: DB5PR10MB7751.EURPRD10.PROD.OUTLOOK.COM
Remote server returned '554 5.7.0 < #5.7.520 smtp;550 5.7.520
  Message blocked because it contains content identified as spam. AS(4810)>'
```

`5.7.520` / `AS(4810)` is a **Microsoft Exchange Online Protection** anti-spam verdict, and
the generating server is a Microsoft host — not `mail.jorisjonkers.dev` (Stalwart at
`167.86.79.203`). The message therefore **never reached Stalwart**. Mail for
`jorisjonkers.dev` is still being routed into Microsoft 365, so the Stalwart catch-all is
out of the delivery path entirely.

Two root causes are possible and are distinguished by a single test (see FR-001):

1. **Public MX still points to Microsoft** (`*.mail.protection.outlook.com`). Then _all_
   inbound mail is captured by Microsoft.
2. **The domain is still a verified accepted domain in a Microsoft 365 tenant.** Microsoft
   routes mail from any Microsoft-hosted sender (the failing test was sent from
   `joriswouter@live.nl`) _internally_ and never performs a public MX lookup — so even a
   correct MX pointing at Stalwart is bypassed for Microsoft-origin senders.

A second, latent gap compounds this: the catch-all **target** mailbox
(`joris.jonkers@jorisjonkers.dev`) is not declared in `infra/stalwart/accounts.json`. Once
mail does reach Stalwart, the catch-all can only deliver if that mailbox exists. Today it
exists, if at all, only as manual webadmin state, so it shares the durability gap that spec
001 closed for the catch-all pointer itself.

DNS records (MX, SPF, DKIM, DMARC, MTA-STS) for `jorisjonkers.dev` are managed **manually
in Cloudflare** per `docs/adr/ADR-003-tls-dns.md` and `dns-zone-policy.md`; `external-dns`
in this cluster only manages `traefik-public` ingress hostnames and does **not** touch mail
records. There is consequently **no in-repo lever** that can change MX/routing — that
remediation is operational and is captured here as a reviewable, design-first runbook.

## User Scenarios & Testing _(mandatory)_

### User Story 1 - Inbound mail reaches Stalwart, not Microsoft (Priority: P1)

As the operator, mail addressed to any `@jorisjonkers.dev` recipient is delivered to my
Stalwart server rather than intercepted by Microsoft 365, so that Stalwart's routing and
catch-all govern delivery.

**Why this priority**: Nothing else works until mail reaches Stalwart. This is the actual
reported failure.

**Independent Test**: From a non-Microsoft sender (e.g. Gmail) and from a Microsoft sender
(e.g. outlook.com), send to a known mailbox and to an unknown local part. Confirm both land
in Stalwart and neither produces a Microsoft `5.7.x` NDR.

**Acceptance Scenarios**:

1. **Given** the cutover is complete, **When** `dig MX jorisjonkers.dev` is queried,
   **Then** it returns `mail.jorisjonkers.dev` and contains no `*.mail.protection.outlook.com`
   host.
2. **Given** the domain has been removed as an accepted domain from the Microsoft 365
   tenant, **When** a Microsoft-hosted sender mails `something@jorisjonkers.dev`, **Then**
   the message is delivered to Stalwart (no internal Microsoft routing, no `5.7.520` NDR).
3. **Given** the cutover is complete, **When** SPF/DKIM/DMARC are evaluated for mail sent
   from Stalwart, **Then** they authorize `mail.jorisjonkers.dev`/`167.86.79.203` and no
   longer authorize Microsoft.

### User Story 2 - Catch-all target mailbox exists declaratively (Priority: P1)

As the operator, the catch-all destination mailbox is provisioned from declarative source,
so that mail to unknown local recipients is actually deliverable and survives a datastore
rebuild — the same guarantee spec 001 gave the catch-all pointer.

**Why this priority**: A catch-all that points at a non-existent mailbox cannot deliver.
This is the in-repo half of the fix and is independently testable.

**Independent Test**: With the declared accounts and a populated Vault secret, run the
reconcile against an empty datastore and confirm the catch-all target account exists with
its declared aliases.

**Acceptance Scenarios**:

1. **Given** `infra/stalwart/accounts.json` declares the catch-all target account, **When**
   the reconcile runs with the target's Vault password populated, **Then** an account for
   `joris.jonkers@jorisjonkers.dev` exists.
2. **Given** the target account declares `extratoast` as an alias, **When** the reconcile
   runs, **Then** mail to `extratoast@jorisjonkers.dev` resolves to the same mailbox.
3. **Given** the target's Vault password key is **absent**, **When** the reconcile runs,
   **Then** the account is skipped and any pre-existing (manually created) mailbox of the
   same address is left untouched — no password is reset and no mailbox is recreated.

### User Story 3 - The remediation is documented and reviewable (Priority: P2)

As the operator, the exact Cloudflare records and Microsoft 365 steps for the cutover are
recorded in-repo as a runbook with a verification checklist, so the operational change is
reviewable and repeatable without copying consumer zone files into the shared repo.

**Acceptance Scenarios**:

1. **Given** the runbook, **When** an operator follows it, **Then** it specifies every
   record (MX, SPF, DKIM, DMARC, MTA-STS, mail host A) with target values and the M365
   accepted-domain removal, plus a post-cutover verification checklist.

## Requirements _(mandatory)_

- **FR-001**: The runbook MUST include the decisive diagnostic (send from a non-Microsoft
  vs. a Microsoft sender) to determine whether the live blocker is MX (#1) or M365 internal
  routing (#2), and MUST give remediation for both.
- **FR-002**: `infra/stalwart/accounts.json` MUST declare the catch-all target account
  (`joris.jonkers`) so the destination mailbox is provisioned by the reconcile.
- **FR-003**: `extratoast@jorisjonkers.dev` MUST resolve to the catch-all target mailbox
  (modelled as an alias of `joris.jonkers`).
- **FR-004**: The target account's password MUST follow the existing Vault-backed,
  **optional** pattern (`account.password`/`n8n.password`): if the Vault key is absent the
  reconcile skips the account and never disturbs existing mailbox state. No raw passwords in
  git.
- **FR-005**: The catch-all target declared by `STALWART_CATCHALL` MUST correspond to a
  provisioned local mailbox or alias; a target with no backing mailbox MUST be caught by an
  automated test.
- **FR-006**: A runnable test harness MUST validate, without network or a live server:
  `accounts.json` is well-formed; the catch-all target is backed by an account or alias;
  the alias/credentials JSON that `apply.sh` builds matches the expected shape; and the
  Vault template + Deployment env wiring for the new password exists and is optional.
- **FR-007**: All changes MUST keep CI green (flux render / kustomize build and existing
  platform validation).

### Key Entities

- **Catch-all target account** — the local mailbox that receives mail for unknown
  recipients; `joris.jonkers@jorisjonkers.dev`. Declared in `accounts.json`, password from
  Vault `secret/platform/mail` key `joris.password` (optional), alias `extratoast`.
- **Cutover runbook** — design-first operational doc: Cloudflare mail records + Microsoft
  365 accepted-domain removal + verification.

## Success Criteria _(mandatory)_

- **SC-001**: After the cutover, a message from a Microsoft-hosted sender to
  `something@jorisjonkers.dev` is delivered to the `joris.jonkers` mailbox via Stalwart with
  no Microsoft `5.7.x` NDR.
- **SC-002**: `dig MX jorisjonkers.dev` returns only `mail.jorisjonkers.dev`.
- **SC-003**: A rebuilt Stalwart datastore, after reconcile with the Vault password
  populated, has the `joris.jonkers` mailbox and the `extratoast` alias.
- **SC-004**: The test harness fails if `STALWART_CATCHALL` is ever pointed at an address
  with no backing account/alias.

## Scope

**In scope**: declarative provisioning of the catch-all target mailbox + alias and its
Vault/env wiring; the cutover runbook; automated consistency tests.

**Out of scope (operator-executed)**: the live Cloudflare DNS edits and the Microsoft 365
tenant changes themselves — these cannot be performed from the repo and are deliberately
documented rather than automated.
