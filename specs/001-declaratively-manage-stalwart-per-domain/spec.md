# Feature Specification: Declaratively Manage Stalwart Per-Domain Catch-All

**Feature Branch**: `001-declaratively-manage-stalwart-per-domain`
**Created**: 2026-06-10
**Status**: Draft
**Input**: User description: "Declaratively manage the Stalwart per-domain catch-all address in personal-stack."

## Context

Stalwart v0.16 models a catch-all as a per-domain property (`Domain.catchAllAddress`):
the address that receives mail for unknown local recipients of that domain. Today this
value is set only through the webadmin UI and lives solely in the RocksDB datastore. It
is absent from git, is not applied by the `stalwart-apply` reconcile sidecar
(`infra/stalwart/apply.sh`), and is therefore lost on any datastore rebuild and
undiscoverable from source — unlike listeners, accounts, domain cert/DNS wiring, and the
Vault-managed accounts, which the reconcile already converges on every pod start.

The observed symptom: mail to `boris@jorisjonkers.dev` (an unknown local recipient) is
not delivered to the operator's mailbox, because no catch-all is reliably configured.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Catch-all survives a datastore rebuild (Priority: P1)

As the operator of the personal-stack mail system, when the Stalwart datastore is rebuilt
from scratch (volume loss, migration, or fresh boot), the domain catch-all is restored
automatically from declarative source so that mail to unknown local recipients keeps
reaching the operator mailbox without any manual webadmin step.

**Why this priority**: This is the core gap. The catch-all is currently fragile manual
state; making it declarative is the whole point of the feature and delivers the durability
guarantee on its own.

**Independent Test**: Start a Stalwart instance with an empty datastore and the declared
configuration, let the reconcile run, then confirm the domain's catch-all address resolves
to the operator mailbox and that mail to an unknown local recipient is accepted and
delivered there.

**Acceptance Scenarios**:

1. **Given** a freshly booted Stalwart with an empty datastore and a declared catch-all
   target, **When** the reconcile completes, **Then** the `jorisjonkers.dev` domain's
   catch-all address equals the declared target mailbox.
2. **Given** the catch-all is configured, **When** a message is sent to an address with no
   matching local mailbox or alias (e.g. `boris@jorisjonkers.dev`), **Then** it is
   accepted and delivered to the catch-all target mailbox.
3. **Given** the catch-all is configured, **When** a message is sent to an existing
   mailbox (e.g. the operator's own address), **Then** it is delivered to that mailbox as
   before and the catch-all does not alter normal routing.

---

### User Story 2 - Configurable catch-all target without code edits (Priority: P2)

As the operator, I can set or change which mailbox receives catch-all mail by changing a
single declared configuration value, without editing reconcile logic.

**Why this priority**: Makes the feature reusable and reviewable, but the P1 durability
guarantee is valuable even with a fixed target.

**Independent Test**: Change the declared catch-all target value, restart the pod, and
confirm the domain catch-all converges to the new value.

**Acceptance Scenarios**:

1. **Given** a declared catch-all target value, **When** the reconcile runs, **Then** the
   domain catch-all is set to exactly that value.
2. **Given** the declared target value is changed to a different mailbox, **When** the pod
   restarts and the reconcile runs, **Then** the domain catch-all converges to the new
   value.

---

### User Story 3 - Safe no-op when unset, no collateral changes (Priority: P3)

As the operator, when no catch-all target is declared, the reconcile leaves whatever
catch-all value already exists in the datastore untouched and never disturbs the existing
domain certificate/DNS wiring.

**Why this priority**: Protects against regressions and accidental data loss, and keeps
the change scoped; lower priority because it is a guard rather than new capability.

**Independent Test**: Run the reconcile with no declared target against a datastore that
has a manually set catch-all, and confirm both the catch-all and the domain cert/DNS
settings are unchanged.

**Acceptance Scenarios**:

1. **Given** no catch-all target is declared, **When** the reconcile runs, **Then** any
   existing catch-all value in the datastore is preserved unchanged.
2. **Given** a catch-all target is declared, **When** the reconcile applies it, **Then**
   the domain's existing certificate-management and DNS-management settings remain intact.
3. **Given** the reconcile runs repeatedly with an unchanged declared target, **Then** the
   resulting domain state is identical after each run (idempotent).

### Edge Cases

- The declared target is an empty string or unset: treated as "not declared" — a no-op
  that preserves existing datastore state (does not clear the catch-all).
- The declared target points at a mailbox that does not (yet) exist: the catch-all value
  is still recorded as declared and the reconcile proceeds; delivery behavior for a missing
  target follows Stalwart's own handling. The reconcile does not verify that the target
  mailbox exists, consistent with how it does not verify alias/group targets today.
- The domain referenced by the catch-all is not present in the datastore: reconcile cannot
  set a catch-all for a missing domain; this should be surfaced rather than silently
  skipped, consistent with how the existing reconcile reports a missing domain.
- The dev/bootstrap environment uses a different domain (`jorisjonkers.test`) than
  production (`jorisjonkers.dev`); the declared catch-all must be expressible per
  environment, not hard-coded to the production domain.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST set the catch-all address of the deployment domain from a
  declared configuration value during the reconcile that runs on every pod start.
- **FR-002**: The declared catch-all target MUST be configurable as a single value per
  environment without modifying reconcile logic.
- **FR-003**: When the declared catch-all target is empty or unset, the system MUST treat
  it as a no-op and MUST NOT clear or modify the existing catch-all value in the datastore.
- **FR-004**: Applying the catch-all MUST NOT clear or alter the domain's existing
  certificate-management or DNS-management settings, nor any other domain field not owned
  by this feature.
- **FR-005**: The reconcile MUST be idempotent: repeated runs with the same declared target
  converge to the same domain state with no spurious changes.
- **FR-006**: When a target is declared and the deployment domain exists, mail to a local
  recipient with no matching mailbox or alias MUST be delivered to the catch-all target.
- **FR-007**: Normal delivery to existing mailboxes and aliases MUST be unaffected by the
  presence of a catch-all.
- **FR-008**: The declared catch-all configuration MUST live in version-controlled source
  so that it is discoverable and restored automatically after a datastore rebuild.
- **FR-009**: The catch-all configuration MUST be expressible separately for the
  production and dev/bootstrap environments, which use different domains.
- **FR-010**: If a catch-all target is declared but the referenced domain is not present in
  the datastore, the system MUST surface the condition rather than silently succeeding.
- **FR-011**: The reconcile MUST NOT gate applying the catch-all on the existence of the
  target mailbox; the declared address is applied as-is, matching how alias/group targets
  are handled today.

### Key Entities *(include if feature involves data)*

- **Domain**: The mail domain (e.g. `jorisjonkers.dev`). Owns the catch-all address
  property along with existing certificate-management and DNS-management wiring. The
  catch-all property is the field this feature manages.
- **Catch-all target**: The single mailbox address that receives mail for unknown local
  recipients of the domain (e.g. the operator mailbox `joris.jonkers@jorisjonkers.dev`).
- **Reconcile run**: The per-pod-start convergence pass that already applies declarative
  settings (listeners, domain wiring, Vault-managed accounts) and that this feature
  extends to also apply the catch-all.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: After a reconcile against an empty datastore with a declared target, the
  domain catch-all equals the declared target with zero manual webadmin steps.
- **SC-002**: Mail sent to an unknown local recipient of the domain is delivered to the
  catch-all target mailbox in 100% of attempts while a target is declared.
- **SC-003**: Running the reconcile two or more times with an unchanged declared target
  produces no change to domain state after the first run (idempotent).
- **SC-004**: With no target declared, an existing manually set catch-all and the domain's
  cert/DNS settings remain byte-for-byte unchanged across a reconcile.
- **SC-005**: The effective catch-all target for each environment is determinable by
  reading version-controlled source alone, without inspecting the live datastore.

## Assumptions

- Stalwart v0.16 continues to model the catch-all as a single per-domain address
  (`Domain.catchAllAddress`), nullable, mutable via the management API/`stalwart-cli apply`.
- The existing reconcile already locates the deployment domain and performs a partial
  domain update; this feature extends that same update path.
- The production catch-all target is the operator mailbox `joris.jonkers@jorisjonkers.dev`;
  the mailbox itself is user-managed and out of scope to create here.
- Secret material is not required for the catch-all value (it is a plain address), so it
  can be carried as ordinary deployment configuration rather than a Vault secret.

## Out of Scope

- Creating or managing the catch-all target mailbox itself (it remains user-managed).
- Per-recipient routing, alias management, sub-addressing, or spam filtering changes.
- Multiple catch-all targets, regex/wildcard recipient rules, or per-tenant catch-all.
- Migrating other manual webadmin state into declarative source beyond the catch-all.
