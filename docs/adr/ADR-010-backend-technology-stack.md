# ADR-010: Backend Technology Stack

## Status

Accepted

## Date

2026-03-25

## Context

We need to choose specific versions and tools for the Kotlin/Spring backend services. The stack must be modern,
well-supported, and consistent across all services.

## Decision

### Core Stack

| Component   | Version               | Notes                                                   |
| ----------- | --------------------- | ------------------------------------------------------- |
| Kotlin      | 2.1.x (latest stable) | K2 compiler, latest language features                   |
| Spring Boot | 4.0.x (latest)        | Bleeding edge — early adoption accepted for new project |
| Java        | 21 LTS                | Virtual threads, pattern matching, sealed classes       |
| Gradle      | Latest stable         | Kotlin DSL (build.gradle.kts)                           |

### Web Framework

- **Spring WebMVC with virtual threads (Java 21)**
- Familiar blocking programming model
- Virtual threads provide async-level performance without reactive complexity
- Chosen over WebFlux: easier to test, debug, and maintain in Kotlin

### Module Boundaries

- **Spring Modulith** for enforcing module boundaries within each service
- Generates module documentation
- Event-based module interaction
- Validates no illegal cross-module dependencies

### Shared Code

- **Gradle composite builds** for shared runtime code (libs/kotlin-common)
- **Gradle convention plugins** for shared build configuration (build-logic/)
- No publishing to Maven repos — direct source inclusion via composite builds

### Key Libraries

- jOOQ (database access)
- Flyway (migrations)
- Spring Authorization Server (auth service)
- Spring Security (all services)
- SpringDoc OpenAPI (API documentation + contract generation)
- MockK (Kotlin mocking)
- AssertJ (assertions)
- ArchUnit (architecture testing)
- Testcontainers (integration testing)
- Micrometer + OpenTelemetry (observability)
- Spring AMQP (RabbitMQ)
- Spring Vault (Vault integration)

## Consequences

- Spring Boot 4.0.x is bleeding edge — may encounter bugs or missing community resources; worth it for a greenfield
  project
- K2 compiler may have edge cases — watch for issues
- Virtual threads simplify concurrency but require awareness of pinning (synchronized blocks)
- Composite builds mean all services must be in the same repo (monorepo — confirmed)
- Convention plugins ensure build consistency but require upfront investment
