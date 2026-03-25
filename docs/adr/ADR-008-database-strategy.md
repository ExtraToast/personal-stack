# ADR-008: Database Strategy

## Status

Accepted

## Date

2026-03-25

## Context

Multiple services need persistent storage. We need to balance resource efficiency (single VPS with 12 GB RAM) against
isolation and choose appropriate data access patterns.

## Decision

### Database Engine

- **PostgreSQL 17** — single instance running in Docker Swarm

### Isolation

- **One database per service** within the shared PostgreSQL instance
- auth-api → `auth_db`
- assistant-api → `assistant_db`
- n8n → `n8n_db`
- Each database has its own user with access restricted to that database only
- Vault provisions database credentials dynamically

### Data Access

- **jOOQ** — type-safe SQL, generates code from database schema
- No ORM magic, no lazy loading gotchas
- Kotlin-friendly: generates data classes, null-safe accessors
- Code generation runs against Flyway-migrated schema

### Migrations

- **Flyway** — SQL-based migrations
- Migration files live in each service's resources/db/migration/
- Naming: V{version}__{description}.sql
- Applied automatically on service startup
- Migration state verified by system tests

### Caching

- **Valkey** (Redis-compatible, FOSS) for session storage and caching
- Auth service uses Valkey for refresh token storage and forward-auth result caching
- Application services may use Valkey for query caching where appropriate

## Consequences

- Single PostgreSQL instance saves ~500MB+ RAM vs per-service instances
- Database-level isolation prevents accidental cross-service queries
- jOOQ code generation adds a build step but catches schema drift at compile time
- Flyway's lack of native rollback means forward-only migrations — rollback via compensating migrations
- Vault dynamic credentials mean services get unique, short-lived DB passwords with audit trail
- Valkey adds one more service but eliminates need for sticky sessions
