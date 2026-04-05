# personal-stack

Self-hosted infrastructure for [jorisjonkers.dev](https://jorisjonkers.dev) — a personal portfolio, AI assistant, and services platform running on Contabo.

## Stack

| Layer         | Technology                                                            |
| ------------- | --------------------------------------------------------------------- |
| Orchestration | Nomad + Consul (single-node, expandable)                              |
| Edge Router   | Traefik with wildcard TLS via Cloudflare DNS-01                       |
| Auth          | Custom OAuth2/OIDC server (Kotlin + Spring Authorization Server)      |
| Backend       | Kotlin 2.1, Spring Boot 4.0, Java 21, Spring WebMVC + virtual threads |
| Frontend      | Vue 3, TypeScript (strict), PrimeVue, Tailwind CSS, Vite              |
| Database      | PostgreSQL 17 (jOOQ + Flyway), Valkey (cache/sessions)                |
| Messaging     | RabbitMQ                                                              |
| Secrets       | HashiCorp Vault (Raft storage, Nomad workload identity)               |
| Automation    | n8n                                                                   |
| Observability | Grafana, Prometheus, Loki, Tempo, Uptime Kuma                         |
| CI/CD         | GitHub Actions, ghcr.io, Nomad job deploys                            |

## Services

| Service       | Type               | URL                        |
| ------------- | ------------------ | -------------------------- |
| app-ui        | Vue                | jorisjonkers.dev           |
| auth-api      | Kotlin Spring Boot | auth.jorisjonkers.dev      |
| auth-ui       | Vue                | auth.jorisjonkers.dev      |
| assistant-api | Kotlin Spring Boot | assistant.jorisjonkers.dev |
| assistant-ui  | Vue                | assistant.jorisjonkers.dev |
| system-tests  | Kotlin             | — (CI + post-deploy)       |

## Project Structure

```
personal-stack/
├── docs/
│   ├── architecture/       # Architecture guide
│   └── adr/                # Architecture Decision Records (ADR-001–020)
├── infra/
│   ├── cloud-init/         # Server provisioning
│   ├── docker/             # Local container assets and DB init scripts
│   ├── nomad/              # Production Nomad jobs, templates, Vault roles
│   ├── scripts/            # Deploy/bootstrap helpers
│   └── traefik/            # Shared Traefik config
├── services/
│   ├── auth-api/           # OAuth2/OIDC auth server
│   ├── auth-ui/            # Login/register/MFA frontend
│   ├── assistant-api/      # AI assistant API
│   ├── assistant-ui/       # Assistant frontend
│   ├── app-ui/             # Marketing/portfolio site
│   └── system-tests/       # Cross-service coherence tests
├── libs/
│   ├── kotlin-common/      # Shared Kotlin code
│   └── vue-common/         # Shared Vue components
├── build-logic/            # Gradle convention plugins
├── settings.gradle.kts     # Gradle composite build root
└── pnpm-workspace.yaml     # Frontend workspaces
```

## Architecture

**Backend:** Hexagonal (Ports & Adapters) with command pattern, domain ownership per module, and Spring Modulith for module boundaries. All mutations flow through immutable commands dispatched by a CommandBus. Cross-domain communication via domain events.

**Frontend:** Feature-based structure with domain boundaries. Composition API + `<script setup>` only. Generated API clients from OpenAPI spec, wrapped by domain adapters. Three-stage validation (UI feedback, Zod schema, backend error reconciliation).

**Auth:** Traefik forward-auth middleware — every request passes through the auth service. Services receive verified user headers. OAuth2/OIDC with JWT tokens (15 min access / 7 day refresh). TOTP MFA from day one.

See [docs/architecture/ARCHITECTURE.md](docs/architecture/ARCHITECTURE.md) for the full guide and [docs/adr/](docs/adr/) for all decision records.

## Code Quality

| Area               | Tools                                                                          |
| ------------------ | ------------------------------------------------------------------------------ |
| Kotlin linting     | detekt + ktlint (no `!!`, no `var`, 30-line function limit, complexity max 10) |
| TypeScript linting | @antfu/eslint-config, strict mode, zero warnings policy                        |
| Formatting         | Prettier (TS/Vue/CSS/JSON), ktlint (Kotlin)                                    |
| Architecture       | ArchUnit (Kotlin), dependency-cruiser (TypeScript), Spring Modulith            |
| Pre-commit         | Husky + lint-staged (TS/Vue), Gradle Git hook (Kotlin)                         |

## Testing

| Level          | Kotlin                                         | Vue/TypeScript            |
| -------------- | ---------------------------------------------- | ------------------------- |
| Unit           | JUnit 5 + MockK + AssertJ                      | Vitest + Vue Test Utils   |
| Integration    | Testcontainers + @SpringBootTest               | Vitest + MSW              |
| E2E            | —                                              | Playwright                |
| Contract       | OpenAPI spec validation                        | OpenAPI spec validation   |
| Architecture   | ArchUnit + Spring Modulith                     | dependency-cruiser        |
| Mutation       | Pitest                                         | Stryker                   |
| Performance    | k6                                             | k6                        |
| Security       | Trivy, OWASP Dep-Check, Semgrep, ZAP, gitleaks | npm audit, Trivy, Semgrep |
| System         | RestAssured + Testcontainers (full stack)      | —                         |
| Infrastructure | Testinfra (over SSH)                           | —                         |

Coverage minimum: **80% line coverage**, enforced in CI.

## CI Pipelines

| Trigger     | Pipeline                                                   | Target   |
| ----------- | ---------------------------------------------------------- | -------- |
| Every push  | Unit tests + lint + type-check                             | < 5 min  |
| PR to main  | Full (integration, E2E, architecture, security, contracts) | < 30 min |
| Nightly     | Full + mutation tests + ZAP + system tests                 | —        |
| Post-deploy | System/coherence tests against live                        | —        |

## Prerequisites

- Java 21
- Kotlin 2.1+
- Node.js 20+
- pnpm 9+
- Docker + Docker Compose

## Getting Started

```bash
# Configure local wildcard DNS and generate the dev TLS certificate for *.jorisjonkers.test
sudo bash infra/scripts/setup-dev-dns.sh

# Start infrastructure services (PostgreSQL, Valkey, RabbitMQ, Vault, Traefik)
docker compose up -d

# Backend (from repo root)
./gradlew build

# Frontend
pnpm install
pnpm -r build
```

## Documentation

- [Architecture Guide](docs/architecture/ARCHITECTURE.md) — comprehensive reference for the entire stack
- [ADR Index](docs/adr/README.md) — all 20 Architecture Decision Records
- [Decision Register](docs/decisions/DECISIONS.md) — original decision questionnaire with rationale
