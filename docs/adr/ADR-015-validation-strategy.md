# ADR-015: Validation Strategy

## Status

Accepted

## Date

2026-03-25

## Context

Validation must happen at multiple levels to catch errors early and provide good user feedback. Backend and frontend
have different validation responsibilities.

## Decision

### Backend — Four-Layer Validation

| Layer           | Responsibility                | Examples                                                                           |
|-----------------|-------------------------------|------------------------------------------------------------------------------------|
| **Web**         | Structural/format validation  | Request body deserializes correctly, required fields present, format (email, UUID) |
| **Command**     | Field-level constraints       | Value ranges, string lengths, enum validity (via annotations or manual checks)     |
| **Application** | Business rules requiring data | "Username not already taken", "User has permission for this action"                |
| **Domain**      | Complex invariants            | Multi-field consistency, state machine transitions, aggregate rules                |

Each layer validates only what it owns. Validation errors return RFC 7807 Problem Details with field-level error arrays.

### Frontend — Three-Stage Pipeline

| Stage                         | When              | Tool                  | Purpose                                                  |
|-------------------------------|-------------------|-----------------------|----------------------------------------------------------|
| **1. UI Interaction**         | On blur/input     | Form validation rules | Real-time user feedback                                  |
| **2. Schema Validation**      | Before submission | Zod                   | Validates command payload shape, catches type mismatches |
| **3. Backend Reconciliation** | After submission  | RFC 7807 parser       | Maps backend Problem Details to field-level errors       |

Form models are independent from transport types — dedicated mappers per command.

## Consequences

- Clear ownership prevents duplicate validation logic
- RFC 7807 Problem Details is a standard — interoperable with any client
- Zod schemas can be derived from OpenAPI spec for consistency
- Frontend validation is always UX-only — backend is the authority
- Four layers add structure but prevent validation logic from accumulating in one place
