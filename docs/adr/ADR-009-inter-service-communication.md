# ADR-009: Inter-Service Communication

## Status

Accepted

## Date

2026-03-25

## Context

Services need to communicate with each other. Some communication is synchronous (request/response), some is
asynchronous (notifications, background processing). We need patterns that keep services decoupled.

## Decision

### Synchronous Communication

- **REST over Docker Swarm overlay network**
- Services address each other by Swarm service name (DNS-based service discovery)
- Authenticated via Vault-issued mTLS certificates
- Used for: queries that need immediate response

### Asynchronous Communication

- **RabbitMQ** for message-based async flows
- Used for: notifications, background processing, cross-service events, email sending
- Exchanges and queues defined per domain event type
- Messages are JSON-serialized domain events

### Within-Service Cross-Module Communication

- **Spring Modulith application events**
- Published after transaction commit (transactional safety)
- Keeps modules within a service decoupled

### Patterns

- Domain events are the primary inter-service communication mechanism
- Services never share databases
- No distributed transactions — eventual consistency with idempotent consumers
- Dead letter queues for failed messages
- Retry with exponential backoff

## Consequences

- RabbitMQ adds ~150MB RAM to the stack
- Need RabbitMQ management UI (accessible via Traefik, admin-only)
- Message schema must be versioned — breaking changes require adapter pattern
- Eventual consistency means UI may show stale data briefly after cross-service operations
- Testcontainers includes RabbitMQ for integration tests
