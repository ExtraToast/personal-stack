# ADR-016: API Contract Management

## Status

Accepted

## Date

2026-03-25

## Context

Frontend and backend must stay in sync. API changes that break the frontend should be caught before deployment, not in
production.

## Decision

### Source of Truth

- **Backend generates OpenAPI spec** via springdoc-openapi
- Spec is generated during the build and committed/published as a build artifact
- The spec is the single source of truth for all API contracts

### Frontend Client Generation

- API clients are auto-generated from the OpenAPI spec
- Generated code lives in a dedicated directory (shared/services/api/generated/)
- **Generated code is never manually edited**
- Generated code is excluded from ESLint and test coverage

### Domain Adapter Pattern

- Each feature wraps the generated client with a domain adapter
- Adapters expose domain models, not generated response types
- Nullability normalization happens at the adapter boundary
- Backend error mapping (RFC 7807 → domain errors) happens at the adapter boundary
- Only adapters are allowed to import from the generated directory

### CI Enforcement

- On every PR: backend builds → generates spec → frontend validates against spec
- Spec mismatch = CI failure
- Any change to generated API clients triggers adapter/mapping review
- CI gates: typecheck + lint + build must all pass

### Versioning

- API versions managed via URL prefix (/api/v1/) or header
- Breaking changes require spec version bump
- Non-breaking additions are backwards-compatible

## Consequences

- API drift caught at build time, not production
- Domain adapter pattern adds a layer but isolates frontend from backend schema changes
- Generated client updates require adapter review — automated via CI checks
- Developers never hand-write API client code — reducing errors and boilerplate
- OpenAPI spec doubles as API documentation
