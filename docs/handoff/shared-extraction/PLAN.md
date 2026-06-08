# Extraction plan (synthesis)

Full detail in `council-consolidated-plan.md`; this is the operator-decided framing.

## Decisions (operator-approved)

- **Order:** migrate **personal-stack first**; website later.
- **Distribution:** published artifacts — Maven (`dev.extratoast.*`) + npm (`@extratoast`) on GitHub Packages; reusable GitHub Actions via pinned `uses:` to `ExtraToast/github-workflows`; container images on `ghcr.io/extratoast`.
- **Ownership:** all shared repos under `ExtraToast`; personal-stack is the canonical source. Either codebase may be refactored to adopt the better pattern; pick the stronger implementation per concern.

## Candidate repositories (classification)

**Extract now — foundational**
- `gradle-conventions` — Gradle convention plugins as Maven plugin-marker artifacts. ⚠️ resolve version drift first (gradle.properties Kotlin 2.1.20 / Spring 4.0.4 / jOOQ 3.19.18 vs build-logic 2.3.21 / 4.0.6 / 3.21.4). jOOQ/OpenAPI/detekt-path/toolchain opt-in. Risk: plugin resolution precedes build.
- `github-workflows` — reusable composite actions (prepare-ci-host, setup-java-gradle, setup-node-*) + reusable workflows; path/host/image/pkg-manager deltas become inputs; setup-node supports pnpm + Yarn Berry.

**Extract now — shared code & tooling**
- `kotlin-spring-commons` — modular Maven artifacts: command, events, blocks, web, exceptions, vault, vault-jwt, observability, timing-web, timing-jooq, crac, email, archunit-test. **Largest dedup win.** Reconcile website's diverged `vault` + typed CommandBus first; messaging deferred (hardcodes `personal-stack.events`/queue names); each Spring add-on owns its `AutoConfiguration.imports`.
- `vue-web-commons` — framework-neutral Vue utils/composables/JWT/Faro/form-toast-mutation as `@extratoast/vue-web-commons` (build output + dist + d.ts + peerDeps). No PrimeVue↔Vuetify convergence forced. `useAuth`/`useTheme` need auth-routes/role/CSRF/storage/theme injected.
- `openapi-client-gradle` + `api-contract-checks` — split external client generation (Gradle plugin) from internal contract-drift checks (scripts/CLI).
- `agent-kit` — renderer/templates/manifest + council; preserve `render-agent-kit.py` flags and the knowledge-api installer-serving model. Already installed into multiple repos.
- `stalwart-provisioner` — schema-driven mail provisioning (GHCR image + validation CLI), v2 schema with `passwordRef` union (env/file/Vault-VSO).

**Extract cautiously — after design**
- `homelab-platform-blueprints` — reusable NixOS/k3s/Flux module subsets + generic bootstrap/validate scripts as a Nix flake input. Keep host data, secrets, app manifests, Nomad/Consul jobs, `fleet.yaml`, render outputs LOCAL.

**Design-only for now**
- `deploy-config-schema` — JSON-schema-driven deploy/infra config from `fleet.yaml` + `platform/tooling` + render scripts; adapters generate Traefik/Gatus/Keel/Nomad inputs.
- `authz-model` — shared permission vocabulary (roles/claims/permissions schema + generated constants). NO auth services / OAuth clients / redirect URIs / domain evaluators merged.

**Do NOT extract:** datastore/ORM (Postgres/jOOQ vs MariaDB/JPA), Nomad/Consul prod jobs, raw DNS zone contents, PrimeVue/Tailwind & Vuetify component systems (first release), personal-stack RabbitMQ event constants, project-specific system-tests, OAuth clients/redirect URIs, concrete permission assignments, association-specific roles.

## Platform differences to RESPECT (keep pluggable, never force-merge)

Nomad+Consul vs k3s+Flux · Postgres/jOOQ/Flyway vs MariaDB/JPA · PrimeVue+Tailwind vs Vuetify · pnpm vs Yarn Berry · Spring-Modulith-hexagonal+CommandBus vs DDD-layered. Commons must not hard-depend on jOOQ-only or PrimeVue-only types unless split into a `-jooq` / `-primevue` add-on artifact.
