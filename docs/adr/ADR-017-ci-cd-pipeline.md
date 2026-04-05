# ADR-017: CI/CD Pipeline

## Status

Accepted

## Date

2026-03-25

## Context

We need automated build, test, and deployment pipelines that enforce quality gates and deploy to the Contabo Nomad
host.

## Decision

### CI System

- **GitHub Actions** — generous free tier, excellent ecosystem, integrated with GitHub

### Container Registry

- **GitHub Container Registry (ghcr.io)** — free for private repos, integrated with Actions, no self-hosting

### Source Code

- **GitHub private repo** — monorepo structure

### Pipeline Architecture

#### Fast Pipeline (every push)

Target: < 5 minutes

- Lint (ESLint + detekt + ktlint)
- Type-check (TypeScript)
- Unit tests (Vitest + JUnit 5)
- Build verification

#### Full Pipeline (PR to main)

Target: < 30 minutes

- Everything in fast pipeline
- Integration tests (Testcontainers)
- E2E tests (Playwright)
- Architecture tests (ArchUnit + dependency-cruiser)
- Contract validation (OpenAPI spec)
- Security scanning (Trivy + npm audit + OWASP Dependency-Check + gitleaks)
- Build Docker images

#### Nightly Pipeline

- Full pipeline
- System/coherence tests
- Mutation testing (Pitest + Stryker)
- OWASP ZAP dynamic security scan
- Semgrep static security analysis

#### Post-Deploy Pipeline

- System/coherence tests against live environment
- Triggered automatically after successful deploy

### Deployment

- **Strategy:** CI pushes images to ghcr.io → authenticated deploy step runs `infra/scripts/deploy.sh` against Nomad
- **Rollouts:** Per-job Nomad `update` stanzas handle rolling replacements when the cluster has spare capacity
- **Singletons:** Single-instance platform jobs may use brief restart-style deploys on small single-node hosts
- Health checks determine readiness before routing traffic

### Deployment Flow

1. PR merged to main
2. Full pipeline runs and passes
3. Docker images built and pushed to ghcr.io with SHA tag + `latest`
4. Authenticate the deploy step against Nomad
5. Run `infra/scripts/deploy.sh` with the target phase/image inputs
6. Nomad applies job updates and rolls allocations according to each job's `update` stanza
7. Post-deploy system tests run
8. On failure: Nomad `auto_revert` or the deploy step fails fast and leaves the previous healthy allocs in place

### Security

- GitHub Actions secrets for: Contabo SSH key, ghcr.io token, Cloudflare API token
- Deploy SSH key scoped to deploy user (not root)
- Images scanned by Trivy before push

## Consequences

- GitHub Actions free tier: 2000 minutes/month for private repos — monitor usage
- Full pipeline at 30 minutes means fast developer feedback on PRs
- Deploys are per-job rather than one atomic stack apply
- Post-deploy tests provide confidence but add time to deploy cycle
- Nightly mutation testing catches weak tests without slowing PR pipeline
