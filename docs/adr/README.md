# Architecture Decision Records

This directory contains all Architecture Decision Records (ADRs) for the private-stack project.

## Index

### Infrastructure & Security

| ADR                                           | Title                                                          | Status   |
| --------------------------------------------- | -------------------------------------------------------------- | -------- |
| [ADR-001](ADR-001-infrastructure-platform.md) | Infrastructure Platform — Contabo, Docker Swarm, Ubuntu 24.04  | Accepted |
| [ADR-002](ADR-002-network-security.md)        | Network Security — UFW, SSH hardening, Fail2ban, rate limiting | Accepted |
| [ADR-003](ADR-003-tls-dns.md)                 | TLS & DNS — Traefik ACME, Cloudflare DNS-01, wildcard cert     | Accepted |
| [ADR-004](ADR-004-secrets-management.md)      | Secrets Management — HashiCorp Vault, Raft, AppRole            | Accepted |

### Authentication & Architecture

| ADR                                                | Title                                                                        | Status   |
| -------------------------------------------------- | ---------------------------------------------------------------------------- | -------- |
| [ADR-005](ADR-005-authentication-authorization.md) | Authentication & Authorization — Spring Auth Server, OAuth2/OIDC, TOTP, RBAC | Accepted |
| [ADR-006](ADR-006-backend-architecture.md)         | Backend Architecture — Hexagonal, command pattern, domain ownership          | Accepted |
| [ADR-007](ADR-007-frontend-architecture.md)        | Frontend Architecture — Feature-based, domain boundaries, composition API    | Accepted |

### Technology Stacks

| ADR                                               | Title                                                                  | Status   |
| ------------------------------------------------- | ---------------------------------------------------------------------- | -------- |
| [ADR-008](ADR-008-database-strategy.md)           | Database Strategy — PostgreSQL 17, jOOQ, Flyway, Valkey                | Accepted |
| [ADR-009](ADR-009-inter-service-communication.md) | Inter-Service Communication — REST + RabbitMQ + Spring Modulith events | Accepted |
| [ADR-010](ADR-010-backend-technology-stack.md)    | Backend Technology Stack — Kotlin 2.1, Spring Boot 4.0, Java 21        | Accepted |
| [ADR-011](ADR-011-frontend-technology-stack.md)   | Frontend Technology Stack — Vue 3, Pinia, PrimeVue, Tailwind, Vite     | Accepted |

### Quality & Testing

| ADR                                           | Title                                                                 | Status   |
| --------------------------------------------- | --------------------------------------------------------------------- | -------- |
| [ADR-012](ADR-012-code-quality-linting.md)    | Code Quality & Linting — detekt, ktlint, ESLint, Prettier, thresholds | Accepted |
| [ADR-013](ADR-013-architecture-testing.md)    | Architecture Testing — ArchUnit, dependency-cruiser, Spring Modulith  | Accepted |
| [ADR-014](ADR-014-testing-strategy.md)        | Testing Strategy — full pyramid, system tests, CI pipelines           | Accepted |
| [ADR-015](ADR-015-validation-strategy.md)     | Validation Strategy — four-layer backend, three-stage frontend        | Accepted |
| [ADR-016](ADR-016-api-contract-management.md) | API Contract Management — OpenAPI, generated clients, domain adapters | Accepted |

### Operations

| ADR                                            | Title                                                           | Status   |
| ---------------------------------------------- | --------------------------------------------------------------- | -------- |
| [ADR-017](ADR-017-ci-cd-pipeline.md)           | CI/CD Pipeline — GitHub Actions, ghcr.io, Swarm rolling deploys | Accepted |
| [ADR-018](ADR-018-monitoring-observability.md) | Monitoring & Observability — Grafana stack, Uptime Kuma         | Accepted |
| [ADR-019](ADR-019-n8n-workflow-automation.md)  | n8n Workflow Automation                                         | Accepted |
| [ADR-020](ADR-020-repository-structure.md)     | Repository Structure — Monorepo, naming conventions             | Accepted |

## How to Add a New ADR

1. Create a new file: `ADR-{NNN}-{short-title}.md`
2. Use the standard MADR format (Status, Date, Context, Decision, Consequences)
3. Add an entry to this index
4. Set status to `Proposed` until reviewed, then `Accepted`
5. To supersede an ADR, set the old one to `Superseded by ADR-{NNN}` and create the new one

## Related Documents

- [Architecture Guide](../architecture/ARCHITECTURE.md) — single comprehensive reference
- [Decision Register](../decisions/DECISIONS.md) — original decision questionnaire with choices
