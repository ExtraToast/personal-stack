# ADR-014: Testing Strategy

## Status

Accepted

## Date

2026-03-25

## Context

Testing is a first-class concern. The stack includes multiple services, infrastructure, and cross-service interactions
that all need verification at appropriate levels.

## Decision

### Test Pyramid

#### Unit Tests

- **Kotlin:** JUnit 5 + MockK + AssertJ
- **Vue:** Vitest + Vue Test Utils
- **Coverage threshold:** 80% line coverage minimum, enforced in CI
- Domain logic gets the highest unit test coverage

#### Integration Tests

- **Kotlin:** Testcontainers (PostgreSQL, Valkey, Vault, RabbitMQ) + @SpringBootTest with test slices (@WebMvcTest,
  @DataJpaTest equivalent for jOOQ, etc.)
- **Vue:** Vitest with MSW (Mock Service Worker) for realistic API interaction mocking
- Real dependencies, no embedded fakes

#### End-to-End Tests

- **Playwright** for all Vue frontends
- Multi-browser (Chromium, Firefox, WebKit)
- Tests real user flows: login, navigation, form submission, error handling
- Runs against a Docker Compose stack with all services

#### Architecture Tests

- **ArchUnit** for Kotlin (see ADR-013)
- **dependency-cruiser** for TypeScript/Vue (see ADR-013)
- **Spring Modulith** module verification

#### Contract Tests

- **OpenAPI spec as source of truth**
- Backend generates spec via springdoc-openapi
- Frontend validates generated client against spec
- CI fails on any spec mismatch

#### Performance Tests

- **k6** for load testing
- JavaScript-based test scripts
- Integrates with Grafana for result visualization
- Run on-demand and nightly

#### Mutation Tests

- **Pitest** for Kotlin/JVM — verifies test quality by mutating source code
- **Stryker** for TypeScript — same principle
- The strictest form of test quality verification
- Run nightly (slow)

#### Security Scanning

- **Trivy:** container image scanning
- **OWASP Dependency-Check:** JVM dependency vulnerabilities
- **npm audit / Trivy:** npm dependency vulnerabilities
- **Semgrep:** static security analysis (code patterns)
- **OWASP ZAP:** dynamic application security testing
- **gitleaks:** secret scanning in code

#### Infrastructure Tests

- **Testinfra** (Python, runs over SSH)
- Verifies cloud-init provisioned correctly:
  - SSH on port 2222, password auth disabled
  - UFW rules active
  - Docker installed and Swarm initialized
  - Users and groups created
  - GPG keys registered
  - Fail2ban running

### System / Coherence Tests

Dedicated Kotlin Spring Boot test service (services/system-tests):

- Uses RestAssured + Testcontainers (Docker Compose for full stack in CI)
- Can also run against live deployment

**What it tests:**

- Health check endpoints of all services
- Traefik routes all subdomains correctly
- TLS certificates valid and not expiring soon
- Full auth flow: register → login → get token → access protected resource
- Auth token rejected when invalid/expired
- Cross-service data flow
- Vault unsealed and secrets accessible
- n8n workflows execute
- Database migrations applied, schema matches expected version
- DNS resolution for all subdomains

**When it runs:**

- Post-deploy (smoke test)
- Nightly scheduled run

### CI Pipeline Strategy

| Trigger     | Pipeline      | Contents                                              | Target Duration |
| ----------- | ------------- | ----------------------------------------------------- | --------------- |
| Every push  | Fast          | Unit tests + linting + type-check                     | < 5 min         |
| PR to main  | Full          | Integration + E2E + system + security + architecture  | < 30 min        |
| Nightly     | Full + extras | Full + mutation tests + security scans + system tests | —               |
| Post-deploy | Smoke         | System/coherence tests against live                   | —               |

## Consequences

- Comprehensive testing adds CI time but catches issues at every level
- Testcontainers requires Docker-in-Docker or a Docker socket in CI runners
- Pitest + Stryker are slow — nightly only
- OWASP ZAP dynamic testing is noisy — may need baseline tuning
- 80% coverage is a minimum, not a target — critical paths should be higher
- System tests require maintaining a Docker Compose file that mirrors production
