# Brief: Identify shared code to extract into standalone repositories

## Context

Two sibling codebases live side-by-side in this workspace and share a large
amount of structure, tooling, and intent. The goal is to **improve alignment**
between them, **stop re-inventing the wheel**, and let improvements to CI,
testing, setup, infra and tooling be made **once** and shared.

- **personal-stack** (`ExtraToast/personal-stack`, working dir `.`) — the more
  mature codebase. Self-hosted platform for jorisjonkers.dev. Kotlin 2.1 /
  Spring Boot 4 / Java 21 hexagonal services, Vue 3 + PrimeVue + Tailwind
  frontends, PostgreSQL (jOOQ + Flyway), Valkey, RabbitMQ, Vault. Nomad +
  Consul orchestration in prod *and* a newer NixOS + k3s + Flux platform layer.
  Rich `libs/kotlin-common`, `libs/vue-common`, `build-logic` convention
  plugins, a full `platform/` (nix, cluster/flux, scripts, agents kit + council),
  and a deep CI suite (10 workflows, 3 composite actions).
- **website / Blueshell** (`ESA-Blueshell/website`, sibling at
  `../website` → absolute `/workspace/website`) — student-association platform.
  Spring Boot 4 (Kotlin) + Vue 3 + **Vuetify** frontend, **MariaDB (JPA)**,
  **Yarn Berry**, NixOS + **k3s + FluxCD** (Keel image automation, no CI deploy
  step). Nearly-empty `libs/kotlin-common` (only `vault/`), `build-logic`
  convention plugins, smaller `platform/` (nix, cluster/flux), `services/api`
  with generated OpenAPI clients (discord, brevo) and `libs/openapi-specs`.

Both projects are owned by the same author. **Decisions already made for this
run:**

1. **Deliverable** = research + a ready-to-execute extraction plan. Do NOT move
   any code in this run. Output a motivated list of candidate shared repos plus
   a parallel task DAG for later execution.
2. **Distribution** = published artifacts. Kotlin/Spring commons + Gradle
   build-logic → **Maven packages on GitHub Packages**; vue-common → **npm
   package on GitHub Packages**. Reusable GitHub Actions → their own repo
   consumed as versioned `uses:` refs. Infra/script/tooling repos consumed as
   appropriate (template repo, CLI install, or pinned action).
3. **Ownership** = all extracted shared repos live under the **`ExtraToast`**
   GitHub org. personal-stack is the canonical source; website is refactored to
   consume the shared artifacts. Either codebase may be refactored to adopt the
   better pattern of the other — pick the stronger implementation per concern.

## Known overlaps already observed (verify + expand, don't take as exhaustive)

- **Kotlin/Spring commons** — personal-stack `libs/kotlin-common` is rich
  (command bus, domain events, observability/tracing, request timing filters,
  RabbitMQ config, Vault secret provider + transit client, web exception
  handling + ProblemDetail, ArchUnit hexagonal rules, CRaC, email). website's
  `libs/kotlin-common` has only `vault/` and it has **diverged**
  (`VaultTransitClient.kt` exists in both but differs; website adds
  `VaultTransitJwtEncoder.kt`). Reconcile and extract the superset.
- **Vue commons** — personal-stack `libs/vue-common` (AppShell, form
  components, useApi/useAuth/useToast/useTheme composables, Faro observability,
  jwt utils). website uses Vuetify with no shared lib. Decide how much is
  framework-agnostic vs PrimeVue/Vuetify-specific.
- **Gradle build-logic** — both have detekt/ktlint/kotlin/spring/testing/
  test-logging convention plugins (largely parallel). personal-stack adds
  jooq-codegen; website adds openapi-client. Extract a shared convention-plugin
  set / version catalog.
- **GitHub Actions** — both have composite actions `prepare-ci-host`,
  `setup-java-gradle`, and `setup-node-pnpm`(personal) vs `setup-node-yarn`
  (website), plus large reusable workflows. Extract reusable workflows +
  actions.
- **Agent / council tooling** — personal-stack `platform/agents/kit` already
  *renders and installs* the council, skills, and KB hooks into both repos from
  templates (`render-agent-kit.py`, `manifest.yaml`, installer). This is
  effectively a product already shipped into both — prime extraction candidate.
- **Stalwart mail provisioning** — `infra/stalwart` is near-identical in both
  (Dockerfile, accounts.json, apply.sh, plan.ndjson.tmpl). Extract a shared
  mail-provisioning module/tool.
- **DNS zones** — both keep a `infra/dns/*.zone` + management approach.
- **Platform / NixOS + k3s + Flux** — both have `platform/nix` (base, k3s,
  roles modules) and `platform/cluster/flux`. personal-stack is far richer
  (profiles, image building, more modules, render/restore/bootstrap scripts).
  This is the "set up servers + scripts" surface the author wants shared.
- **OpenAPI / API clients / docs** — website has `libs/openapi-specs` +
  generated clients (discord, brevo) + openapi generation scripts; personal-
  stack has contract validation + generated clients. Shared OpenAPI tooling and
  a "general API clients" approach is an explicit goal.
- **JSON-Schema-driven deploy/infra config** — the author explicitly wants a
  *simple JSON-schema-based way to manage deployments and infra* shared between
  both. This likely does not fully exist yet; assess what each has (Nomad job
  templates + render scripts vs Flux manifests + render scripts) and propose the
  shared schema + tooling repo.
- **Permission structures** — auth/permission model. personal-stack has a full
  OAuth2/OIDC auth-api + Traefik forward-auth; website issues tokens via Spring
  Authorization Server. Author wants *common permission structures* shared.
- **System tests / code-quality config** — both have `services/system-tests`,
  shared detekt config, prettier/eslint, gitleaks. Assess shared test harness +
  shared lint/quality config + CI validation of those tools.

## Platform differences to RESPECT (do not force-merge)

- Orchestration: personal-stack still runs **Nomad + Consul** in prod; website
  is **k3s + Flux** only. (personal-stack also has a k3s nix layer — note the
  convergence but don't assume it.)
- Datastore/ORM: **PostgreSQL + jOOQ + Flyway** vs **MariaDB + JPA**.
- Frontend UI kit: **PrimeVue + Tailwind** vs **Vuetify**.
- Package manager: **pnpm** vs **Yarn Berry**.
- Domain style: **Spring Modulith hexagonal + CommandBus** vs **DDD layered**.

Shared repos must be designed so these differences stay pluggable (e.g. commons
must not hard-depend on jOOQ-only or PrimeVue-only types unless cleanly
separated into a `-jooq` / `-primevue` add-on artifact).

## Your task

1. **Inventory & verify** the genuinely shared (or trivially alignable) surface
   across both repos. Read both — personal-stack at `.` and website at
   `/workspace/website`. Distinguish: (a) identical/near-identical duplication,
   (b) same concern, divergent implementation (pick the better one),
   (c) exists in one, wanted in both, (d) aspirational/shared-goal-not-built-yet.
2. **Propose a concrete list of extraction-candidate repositories.** For each:
   - proposed repo name (under `ExtraToast/…`) and one-line purpose;
   - what moves into it (paths from each repo), and which implementation wins
     when they diverge, with justification;
   - distribution mechanism (Maven pkg / npm pkg / reusable-actions repo /
     template repo / CLI) consistent with the decisions above;
   - how each project consumes it and what changes in each consumer;
   - **motivation**: concrete duplication removed / alignment gained / why it's
     worth a separate repo vs leaving in place;
   - effort (S/M/L), risk, and ordering/dependencies between extractions;
   - explicit call-out if a concern should NOT be extracted (and why).
3. **Sequence it.** Produce a dependency-ordered DAG of extraction tasks
   (foundational/low-risk first: build-logic, actions, kotlin-common; then
   vue-common, infra modules, agent-kit; then the aspirational schema-driven
   deploy + permissions work last as design tasks). Each task should be
   independently executable by a worker later.

## Definition of done

`consolidated_plan.md` containing the motivated repo list + the per-repo detail
above, and `tasks.json` expressing the extraction DAG. No code moved.
