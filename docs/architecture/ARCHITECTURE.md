# Personal Stack -- Architecture Guide

## Overview

Self-hosted private stack for jorisjonkers.dev on Contabo. Nomad orchestration with Consul service discovery, Traefik
edge routing, HashiCorp Vault for secrets, custom auth, multiple Vue + Kotlin services, n8n automation, and a full
Grafana observability stack.

## Domain: jorisjonkers.dev

- Subdomain per service: auth.jorisjonkers.dev, assistant.jorisjonkers.dev, vault.jorisjonkers.dev,
  n8n.jorisjonkers.dev, grafana.jorisjonkers.dev, etc.
- Main marketing site at jorisjonkers.dev
- Cloudflare DNS (free tier, DNS-only mode, grey cloud)
- Wildcard TLS via Traefik ACME DNS-01 challenge with Cloudflare API

## Infrastructure

- Contabo Cloud VPS 20 (6 vCPU, 12 GB RAM, 400 GB SSD)
- Ubuntu 24.04 LTS
- Single-node Nomad cluster with Consul (expandable)
- Cloud-init for provisioning + Contabo API automation (no Terraform)

## Network Security

- UFW firewall: allow 2222/tcp (SSH), 80/tcp (HTTP redirect), 443/tcp (HTTPS), deny all else
- SSH on port 2222, key-only auth, password disabled
- Fail2ban: SSH + Traefik jails
- No VPN (may add WireGuard later)
- Traefik rate limiting: per-IP + per-authenticated-user
- Public marketing pages, all app functionality requires auth
- No IP allowlisting on admin services (auth-only for now)

## Services

| Service       | Type               | Subdomain                  | Description                                              |
| ------------- | ------------------ | -------------------------- | -------------------------------------------------------- |
| app-ui        | Vue                | jorisjonkers.dev           | Marketing/portfolio page                                 |
| auth-api      | Kotlin Spring Boot | auth.jorisjonkers.dev      | Centralized OAuth2/OIDC auth server                      |
| auth-ui       | Vue                | auth.jorisjonkers.dev      | Login/register/MFA page                                  |
| assistant-api | Kotlin Spring Boot | assistant.jorisjonkers.dev | AI assistant API (code changes, PRs, automations, voice) |
| assistant-ui  | Vue                | assistant.jorisjonkers.dev | Assistant frontend                                       |
| system-tests  | Kotlin Spring Boot | --                         | Cross-service coherence test suite                       |

## Infrastructure Services

| Service         | Subdomain                | Notes                                        |
| --------------- | ------------------------ | -------------------------------------------- |
| Traefik         | traefik.jorisjonkers.dev | Edge router, TLS termination, forward-auth   |
| Vault           | vault.jorisjonkers.dev   | Secrets management, behind Traefik + auth    |
| PostgreSQL 17   | internal only            | One instance, separate DB per service        |
| Valkey          | internal only            | Session store + cache                        |
| RabbitMQ        | internal only            | Async messaging between services             |
| n8n             | n8n.jorisjonkers.dev     | Workflow automation, behind centralized auth |
| Grafana         | grafana.jorisjonkers.dev | Dashboards for logs/metrics/traces           |
| Prometheus      | internal only            | Metrics collection                           |
| Loki + Promtail | internal only            | Log aggregation                              |
| Tempo           | internal only            | Distributed tracing                          |
| Uptime Kuma     | status.jorisjonkers.dev  | Uptime monitoring                            |

## Authentication & Authorization

- Custom Kotlin auth service using Spring Authorization Server
- OAuth2/OIDC with JWT access tokens
- Token lifetimes: 15 min access / 7 day refresh
- Traefik forward-auth middleware -- every request goes through auth service
- TOTP MFA from day one
- Simple RBAC (admin/user/readonly) -- designed to evolve to fine-grained RBAC
- Service-to-service: Vault-issued short-lived tokens / mTLS

## Secrets Management (Vault)

- Raft integrated storage
- Manual unseal with Shamir keys (pending confirmation -- may switch to auto-unseal)
- Nomad-issued Vault tokens via workload identity for services
- Manages: DB creds (dynamic), JWT signing keys, TLS certs (PKI), API keys, Docker registry creds, encryption keys, SSH
  CA

## Backend Architecture (Kotlin)

- Kotlin 2.1.x, Spring Boot 4.0.x, Java 21 LTS
- Spring WebMVC with virtual threads
- Spring Modulith for module boundaries
- Hexagonal / Ports & Adapters architecture
- Gradle Kotlin DSL, composite builds + convention plugins
- jOOQ for database access
- Flyway for migrations

### Hexagonal Package Structure

```
com.jorisjonkers.personalstack.<service>/
  domain/           # Pure domain logic, no framework dependencies
    model/          # Entities, value objects
    port/           # Interfaces (inbound + outbound)
    event/          # Domain events
  application/      # Use cases, command handlers, orchestration
    command/        # Command objects + handlers
    query/          # Query services
  infrastructure/   # Framework adapters
    web/            # Controllers (REST endpoints)
    persistence/    # jOOQ repositories, Flyway migrations
    messaging/      # RabbitMQ publishers/consumers
    security/       # Permission evaluators, auth config
    integration/    # External system ACL adapters
  config/           # Spring configuration
```

### Backend Architecture Rules (ArchUnit enforced)

1. Strict layered: Web -> Application -> Domain <- Infrastructure
2. Domain has zero framework dependencies
3. Command pattern: mutations via immutable commands + CommandBus
4. Data ownership: each domain owns its tables, cross-domain via events only
5. Anti-corruption layers for external systems
6. No entity/record exposure at API boundaries -- DTOs only, manual mapping
7. Constructor injection only (no @Autowired fields)
8. Security on every endpoint: @PreAuthorize or @PermitAll required
9. @Transactional only in application/domain layers
10. Event-driven cross-domain communication (Spring Modulith events)

### Validation Strategy (Backend)

Four layers:

1. **Web**: structural/format (request body shape)
2. **Command**: field-level constraints (annotations)
3. **Application**: business rules (DB lookups)
4. **Domain**: complex invariants

## Frontend Architecture (Vue)

- Vue 3 with Composition API + `<script setup>` only
- Pinia for state management
- Vue Router 4
- PrimeVue (component library)
- Tailwind CSS
- Vite build tool
- pnpm workspaces
- Shared libs/vue-common package

### Feature-Based Structure

```
src/
  features/
    auth/
      components/
      composables/
      services/
      stores/
      types/
      views/
    dashboard/
      ...
  shared/
    components/    # Base/common components (domain-agnostic)
    composables/   # Shared composables
    services/      # API client, HTTP utils
    types/         # Shared types
  router/
  stores/          # Global stores only
  App.vue
```

### Frontend Architecture Rules (dependency-cruiser enforced)

1. No circular imports
2. Components cannot import from views
3. API calls only from services layer
4. Store actions go through service layer
5. Path aliases required for cross-module imports
6. No backend/shared-backend imports in Vue code
7. Domain-first package structure with barrel exports
8. Generated API clients never manually edited
9. Domain adapter boundary: components consume domain models, never generated types
10. Route authorization via metadata (requiresAuth, requiredRoles, featureFlag)

### Validation Strategy (Frontend)

Three stages:

1. **UI interaction feedback**: validation rules on form fields
2. **Schema validation**: Zod for command payload shape
3. **Backend error reconciliation**: RFC 7807 Problem Details -> field errors

## Inter-service Communication

- Synchronous: REST over Nomad networking with Consul-backed service discovery
- Asynchronous: RabbitMQ for decoupled flows (notifications, background jobs)
- Cross-domain: Spring Modulith domain events (within service), RabbitMQ (between services)

## API Contracts

- OpenAPI spec as source of truth
- Backend generates spec via springdoc-openapi
- Frontend validates against spec in CI
- Generated API clients wrapped by domain adapters
- CI fails on spec mismatch

## Code Quality

### TypeScript/Vue

- TypeScript strict: `strict: true` + `noUncheckedIndexedAccess` + `exactOptionalPropertyTypes`
- ESLint: @antfu/eslint-config + custom strict rules
- Prettier for formatting
- Zero lint warnings policy (--max-warnings 0)

### Kotlin

- detekt + ktlint
- Max function: 30 lines, max file: 300 lines
- Cyclomatic complexity: max 10
- `!!` forbidden (error), `var` forbidden (error), explicit return types on public functions
- Constructor injection only

### Shared

- .editorconfig at repo root
- Pre-commit hooks: Husky + lint-staged (TS/Vue) + Gradle Git hook (Kotlin)

## Testing Strategy

### Test Pyramid

| Level          | Kotlin Tools                                       | Vue Tools                   | Coverage    |
| -------------- | -------------------------------------------------- | --------------------------- | ----------- |
| Unit           | JUnit 5 + MockK + AssertJ                          | Vitest + Vue Test Utils     | 80% minimum |
| Integration    | Testcontainers + @SpringBootTest slices            | Vitest + MSW                | --          |
| E2E            | --                                                 | Playwright                  | --          |
| Architecture   | ArchUnit + Spring Modulith                         | dependency-cruiser          | --          |
| Contract       | springdoc OpenAPI validation                       | OpenAPI spec validation     | --          |
| Performance    | k6                                                 | k6                          | --          |
| Mutation       | Pitest                                             | Stryker                     | --          |
| Security       | Trivy + OWASP Dep-Check + Semgrep + ZAP + gitleaks | npm audit + Trivy + Semgrep | --          |
| Infrastructure | Testinfra (Python, over SSH)                       | --                          | --          |
| System         | RestAssured + Testcontainers (full stack)          | --                          | --          |

### System / Coherence Tests

Dedicated Kotlin service that tests all apps together:

- Health checks for all services
- Traefik routing validation
- TLS certificate validity
- Full auth flow (register -> login -> token -> protected resource)
- Token rejection on invalid/expired
- Cross-service data flow
- Vault unsealed + secrets accessible
- n8n workflow execution
- Database migration verification
- DNS resolution for all subdomains
- Runs: post-deploy smoke + nightly scheduled

### CI Pipeline Strategy

| Trigger     | Pipeline      | Contents                                             | Target   |
| ----------- | ------------- | ---------------------------------------------------- | -------- |
| Every push  | Fast          | Unit tests + linting + type-check                    | < 5 min  |
| PR to main  | Full          | Integration + E2E + system + security + architecture | < 30 min |
| Nightly     | Full + extras | Full pipeline + system tests + security scans        | --       |
| Post-deploy | Smoke         | System/coherence tests                               | --       |

## CI/CD

- GitHub Actions
- GitHub Container Registry (ghcr.io)
- Deploy: CI pushes image -> authenticated Nomad deploy via `infra/scripts/deploy.sh`
- Rolling updates handled per job through Nomad `update` stanzas when capacity allows
- Source: GitHub private repo

## Monitoring & Observability

- Logging: Loki + Promtail + Grafana
- Metrics: Prometheus + Grafana (Spring Boot Actuator)
- Tracing: OpenTelemetry + Tempo + Grafana
- Uptime: Uptime Kuma (self-hosted)
- Alerting: Email + Discord

## n8n

- Workflow automation for: deploys, alert routing, scheduled tasks, data sync, external APIs
- Separate database in shared PostgreSQL
- Behind centralized auth

## Repository Structure

```
personal-stack/
  docs/
    architecture/        # This guide
    adr/                 # Architecture Decision Records
  infra/
    cloud-init/
    docker/
    nomad/
      jobs/
      templates/
      vault/
    observability/
    scripts/
    traefik/
  services/
    auth-api/
    auth-ui/
    assistant-api/
    assistant-ui/
    app-ui/
    system-tests/
  libs/
    kotlin-common/
    vue-common/
  build-logic/
  .github/workflows/
  .editorconfig
  .gitignore
  docker-compose.yml
  settings.gradle.kts
  pnpm-workspace.yaml
```

CLAUDE.md exists locally only (not committed).

## Naming Conventions

| Item                  | Convention                               |
| --------------------- | ---------------------------------------- |
| Directories           | kebab-case                               |
| Kotlin packages       | com.jorisjonkers.personalstack.{service} |
| Vue components        | PascalCase.vue                           |
| TypeScript files      | camelCase.ts                             |
| Docker services       | kebab-case                               |
| Databases             | snake_case                               |
| API endpoints         | /kebab-case                              |
| Environment variables | SCREAMING_SNAKE_CASE                     |

## ADR Index

See docs/adr/ for individual Architecture Decision Records.
