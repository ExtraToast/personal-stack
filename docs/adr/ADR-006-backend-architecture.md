# ADR-006: Backend Architecture — Hexagonal with Domain Ownership

## Status

Accepted

## Date

2026-03-25

## Context

Backend services need a clear, testable architecture that enforces separation of concerns, prevents domain logic leakage
into infrastructure, and scales as services grow. Patterns from a prior project (strict layering, command pattern,
domain ownership, ACLs) have proven effective and should be carried forward.

## Decision

### Architectural Style

**Hexagonal Architecture (Ports & Adapters)** for all Kotlin Spring Boot services.

### Package Structure

```
com.jorisjonkers.privatestack.<service>/
├── domain/           # Pure domain logic — ZERO framework dependencies
│   ├── model/        # Entities, value objects, aggregates
│   ├── port/         # Interfaces: inbound (use case) + outbound (repository, messaging)
│   └── event/        # Domain events
├── application/      # Use cases, orchestration
│   ├── command/      # Immutable command objects + handlers (one handler per command)
│   └── query/        # Query services
├── infrastructure/   # Adapters implementing ports
│   ├── web/          # REST controllers
│   ├── persistence/  # jOOQ repository implementations
│   ├── messaging/    # RabbitMQ adapters
│   ├── security/     # Permission evaluators, Spring Security config
│   └── integration/  # External system ACL adapters
└── config/           # Spring @Configuration classes
```

### Dependency Rules (enforced by ArchUnit)

1. **Strict layering:** Web → Application → Domain. Infrastructure implements domain ports.
2. **Domain purity:** Domain package has zero imports from Spring, jOOQ, Jackson, or any framework
3. **No circular dependencies** between packages
4. **Controllers never access repositories** — must go through application layer

### Command Pattern

- All mutations go through immutable command objects dispatched by a CommandBus
- One handler per command
- Commands live in application/command/ and must not import web or infrastructure code
- Queries are separate from commands (CQRS-lite)

### Data Ownership

- Each domain owns its database tables exclusively
- Cross-domain data access only through:
  - Domain events (preferred, via Spring Modulith)
  - Application-layer service calls (when synchronous response needed)
- Never reach into another domain's repositories

### Anti-Corruption Layers

- Every external integration is isolated behind an ACL adapter in infrastructure/integration/
- Production adapters use @Profile("prod"), mock adapters use @Primary for testing
- Domain never depends on external system DTOs

### API Boundaries

- Controllers must never expose jOOQ records or domain entities in method signatures
- Only DTOs at API boundaries
- No mapping libraries — mapping is manual and explicit
- Request → Command mapping at web layer
- Entity/Record → Response DTO mapping at web layer

### Dependency Injection

- **Constructor injection only** — @Autowired on fields is banned
- Enforced by ArchUnit

### Transaction Management

- @Transactional only in application or domain service layers
- Never on controllers
- Never on persistence layer (let the caller control the transaction boundary)

### Event-Driven Cross-Domain Communication

- Within a service: Spring Modulith application events
- Between services: RabbitMQ
- Events published after commit (transactional safety)

## Consequences

- Higher upfront structure cost — package discipline required from day one
- Extremely testable: domain logic testable without any framework
- ArchUnit tests enforce all rules automatically in CI
- Spring Modulith validates module boundaries
- Command pattern adds some boilerplate but provides audit trail and clear mutation paths
- ACL pattern means external system changes don't ripple into domain code
