# ADR-020: Repository Structure

## Status

Accepted

## Date

2026-03-25

## Context

We need to organize a monorepo containing Kotlin backends, Vue frontends, shared libraries, infrastructure
configuration, and documentation. The structure must support Gradle composite builds and pnpm workspaces simultaneously.

## Decision

### Monorepo

Single GitHub private repository containing everything.

### Directory Structure

```
private-stack/
├── docs/
│   ├── architecture/          # Architecture guide (ARCHITECTURE.md)
│   └── adr/                   # Architecture Decision Records (this directory)
├── infra/
│   ├── cloud-init/            # Server provisioning scripts
│   ├── docker/                # Swarm stack files, shared Dockerfiles
│   ├── traefik/               # Traefik static + dynamic config
│   └── vault/                 # Vault config, policies, AppRole definitions
├── services/
│   ├── auth-api/              # Kotlin Spring Boot — OAuth2/OIDC auth server
│   ├── auth-ui/               # Vue — login/register/MFA frontend
│   ├── assistant-api/         # Kotlin Spring Boot — AI assistant API
│   ├── assistant-ui/          # Vue — assistant frontend
│   ├── app-ui/                # Vue — marketing/portfolio site
│   └── system-tests/          # Kotlin — cross-service coherence tests
├── libs/
│   ├── kotlin-common/         # Shared Kotlin: DTOs, utils, Vault client, auth helpers
│   └── vue-common/            # Shared Vue: components, composables, types, theme
├── build-logic/               # Gradle convention plugins (shared build config)
├── .github/workflows/         # CI/CD pipeline definitions
├── .editorconfig              # Editor configuration
├── .gitignore
├── docker-compose.yml         # Local development stack
├── docker-compose.prod.yml    # Production Swarm stack
├── settings.gradle.kts        # Gradle composite build root
└── pnpm-workspace.yaml        # Frontend workspace configuration
```

### Important: No CLAUDE.md in Repository

CLAUDE.md exists locally only (gitignored). It is not committed to the repository.

### Naming Conventions

| Item                  | Convention                              | Example                              |
|-----------------------|-----------------------------------------|--------------------------------------|
| Directories           | kebab-case                              | `auth-api`, `vue-common`             |
| Kotlin packages       | com.jorisjonkers.privatestack.{service} | `com.jorisjonkers.privatestack.auth` |
| Vue component files   | PascalCase.vue                          | `LoginForm.vue`                      |
| TypeScript files      | camelCase.ts                            | `useAuth.ts`                         |
| Docker service names  | kebab-case                              | `auth-api`, `assistant-ui`           |
| Database names        | snake_case                              | `auth_db`, `assistant_db`            |
| API endpoints         | /kebab-case                             | `/api/v1/user-profile`               |
| Environment variables | SCREAMING_SNAKE_CASE                    | `DATABASE_URL`                       |

### Gradle Configuration

- `settings.gradle.kts` at root includes all Kotlin projects (services + libs)
- `build-logic/` contains convention plugins applied by all Kotlin projects
- Convention plugins standardize: Kotlin version, Spring Boot version, testing dependencies, linting, code generation

### pnpm Configuration

- `pnpm-workspace.yaml` lists: `services/*/` (Vue projects) + `libs/vue-common`
- Root `package.json` has workspace-level scripts for linting, testing, building all frontends
- Shared ESLint and Prettier configs at root level

### Local Development

- `docker-compose.yml` starts all infrastructure services (PostgreSQL, Valkey, RabbitMQ, Vault, Traefik)
- Backend services run via IntelliJ/Gradle
- Frontend services run via Vite dev server
- Testcontainers used in tests (doesn't rely on docker-compose)

## Consequences

- Monorepo enables atomic cross-service changes (API + frontend in one PR)
- Gradle composite builds give instant cross-project compilation
- pnpm workspaces enforce strict dependency resolution
- Single CI pipeline for all services — can be parallelized per service in GitHub Actions matrix
- Repository size will grow — use .gitignore aggressively for build artifacts
- CLAUDE.md being local-only means each developer maintains their own — AI conventions must be in docs/architecture/
  instead
