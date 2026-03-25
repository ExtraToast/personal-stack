# ADR-013: Architecture Testing

## Status

Accepted

## Date

2026-03-25

## Context

Architecture rules are only useful if they're enforced automatically. Manual code review cannot catch all violations. We
need automated tests that fail when architecture rules are broken.

## Decision

### Kotlin/Java — ArchUnit + Spring Modulith

#### ArchUnit Rules

All enforced as JUnit 5 tests in each service's test suite:

1. **No circular dependencies** between packages
2. **Controllers must not access repositories** — must go through application/service layer
3. **No Spring annotations in domain layer** — domain is framework-free
4. **Naming conventions:** classes must follow `*Controller`, `*Service`, `*Repository`, `*UseCase`, `*Command`,
   `*Handler` patterns
5. **No field injection** — @Autowired on fields is forbidden; constructor injection only
6. **Domain objects must not depend on infrastructure** — no jOOQ records, no DTOs in domain
7. **Layered architecture enforcement:** web → application → domain, infrastructure implements domain ports
8. **Command pattern enforcement:** commands in application/command/ must not import web or infrastructure
9. **@Transactional placement:** only on application/domain service layers, never on controllers or persistence
10. **Security annotations:** all controller endpoints must have @PreAuthorize or @PermitAll; @PreAuthorize must use
    hasPermission()
11. **Data ownership:** repository implementations must not access tables owned by other domains
12. **Anti-corruption layers:** external integration code confined to infrastructure/integration/

#### Spring Modulith

- Validates module boundaries within each service
- Generates module dependency documentation
- Catches illegal cross-module access at test time

### TypeScript/Vue — dependency-cruiser

All enforced as dependency-cruiser rules in .dependency-cruiser.cjs:

1. **No circular imports**
2. **Components cannot import from views** — views import components, not the reverse
3. **API calls only from services layer** — components and stores must not import HTTP clients
4. **Store actions go through service layer** — stores must not directly call API
5. **Path aliases for cross-module imports** — no relative imports crossing feature boundaries
6. **No backend code imports** — Vue apps must not import from backend/shared-backend
7. **Domain boundaries:** features must not deep-import from other features — use barrel exports
8. **Generated API clients:** only domain adapters may import from generated/ directory
9. **Shared components domain-agnostic:** shared/components/ must not import from features/

## Consequences

- Architecture violations caught at test time, not code review
- New developers guided by automated tests — architecture is self-documenting
- Adding new rules requires writing new ArchUnit/dependency-cruiser tests
- CI time increases slightly but prevents costly architectural drift
- Spring Modulith module docs auto-generated for each service
