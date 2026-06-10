# Requirements Quality Checklist

Spec: `specs/001-declaratively-manage-stalwart-per-domain/spec.md`

## Completeness

- [x] CHK001 — Primary user journey (catch-all survives datastore rebuild) is described with value rationale.
- [x] CHK002 — Each user story has an independent test and acceptance scenarios.
- [x] CHK003 — Edge cases cover unset value, missing target mailbox, missing domain, and dev-vs-prod domain split.
- [x] CHK004 — Assumptions and out-of-scope items are stated.

## Testability & Measurability

- [x] CHK005 — Acceptance scenarios use Given/When/Then with observable outcomes.
- [x] CHK006 — Success criteria are measurable (set value equals target, 100% delivery, idempotent no-change, byte-for-byte preservation, source-determinable).
- [x] CHK007 — Idempotency and no-op-on-empty are expressed as verifiable requirements (FR-003, FR-005, SC-003, SC-004).

## Scope & Clarity

- [x] CHK008 — Scope bounded to the per-domain catch-all; alias/sub-addressing/spam/multi-target explicitly excluded.
- [x] CHK009 — Requirements avoid prescribing implementation (no script/flag/env names in FRs; mechanism deferred to plan).
- [x] CHK010 — Non-clobber guarantee for cert/DNS and other domain fields is explicit (FR-004).

## Open Questions

- [x] CHK011 — Resolved: reconcile proceeds and applies the declared address without verifying the target mailbox exists (FR-011), matching existing alias/group handling.
