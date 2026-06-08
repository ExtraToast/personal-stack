# Consolidated Extraction Plan

## Evidence and gates

personal-stack is the canonical source and has verified local evidence for the key surfaces: root Gradle include-build wiring in settings.gradle.kts, stale version declarations in gradle.properties, build-logic convention plugins, libs/kotlin-common packages, private raw-source libs/vue-common packaging, Dockerfiles that copy build-logic and libs, OpenAPI scripts in package.json and service build files, agent-kit renderer and installer serving, Stalwart provisioning files, and coupled CI workflows.

The website repo must be verified before any website-mutating work starts. Local ../website access is the hard gate for execution because the brief is about sibling repos and local dirty or generated state matters. GitHub MCP/default-branch reads are acceptable only as preliminary evidence and must be pinned by ref; they do not replace local verification for implementation winners or consumer rewiring.

## Classification

Extract now: Gradle conventions, GitHub actions/workflows, Kotlin/Spring commons, Vue web commons, agent kit, Stalwart provisioner, OpenAPI tooling split into client generation and contract checks.

Extract cautiously after design: homelab platform blueprints.

Design only for now: schema-driven deploy config and shared authorization model.

Do not extract as raw shared code: datastore/ORM implementation, Nomad/Consul production jobs, raw DNS zone contents, PrimeVue/Tailwind or Vuetify component systems in the first Vue release, personal-stack RabbitMQ event constants, project-specific system-test suites, OAuth clients, redirect URIs, concrete service-permission assignments, and association-specific roles.

## Candidate repositories

### ExtraToast/gradle-conventions
Purpose: Maven-published Gradle convention plugins consumed from GitHub Packages.

Move: personal-stack build-logic/src/main/kotlin and build-logic/build.gradle.kts patterns; website build-logic equivalents after local verification. personal-stack wins for maturity, but not as-is. The shared repo must publish plugin marker artifacts, define stable plugin IDs, add TestKit coverage, and make Java toolchain, detekt config path/default resource, integration-test model, Jacoco thresholds, jOOQ codegen, and OpenAPI-client generation opt-in. Resolve the stale version story before publishing: gradle.properties currently lists Kotlin 2.1.20, Spring Boot 4.0.4, and jOOQ 3.19.18 while build-logic uses Kotlin 2.3.21, Spring Boot 4.0.6, and jOOQ 3.21.4 with a 3.21.2 runtime comment.

Distribution: Maven package on GitHub Packages under dev.extratoast.

Consumption: both repos replace includeBuild build-logic with pluginManagement plugin coordinates, GitHub Packages credentials in pluginManagement.repositories, and GitHub Packages credentials in dependencyResolutionManagement.repositories. Keep a staged fallback until fresh-clone local dev, CI, and Docker builds resolve plugins.

Motivation: removes parallel Gradle convention plugins and creates one place for testing, lint, Java/Kotlin, Spring, jOOQ, and OpenAPI build behavior. Effort M/L, risk high because plugin resolution happens before the build can run.

### ExtraToast/github-workflows
Purpose: versioned reusable composite actions and small workflows.

Move: .github/actions/prepare-ci-host, setup-java-gradle, setup-node-pnpm, website setup-node-yarn equivalents, and reusable workflow fragments only after all path, host, image, script, and package-manager differences become inputs. Do not lift personal-stack system-tests.yml wholesale until .github/scripts, infra/scripts/run-strict-command.sh, DNS setup, GHCR image names, hostnames, permissions, and callers full.yml and nightly.yml are parameterized.

Distribution: standalone actions repo consumed through pinned uses refs and tags, ideally public or explicitly allowed cross-org.

Consumption: both repos replace local composite actions gradually. setup-java-gradle must accept GitHub Packages credentials because Gradle will pull published plugins and libraries. setup-node must support pnpm and Yarn Berry.

Motivation: removes duplicated CI setup and fixes action deltas once. Effort M, risk medium.

### ExtraToast/kotlin-spring-commons
Purpose: Maven modules for reusable Kotlin/Spring code.

Move: personal-stack libs/kotlin-common packages archunit, blocks, command, crac, email, event, exception, observability, timing, vault, web, and only generic messaging abstractions. Reconcile website libs/kotlin-common vault files and typed CommandBus locally before choosing APIs. Proposed modules: command, events, blocks, web, exceptions, vault, vault-jwt, observability, timing-web, timing-jooq, crac, email, archunit-test. ArchUnit is test-support only, not runtime core. Messaging is deferred or abstractions-only because RabbitMqConfig hardcodes personal-stack.events, auth.user.registered, and queue names.

Implementation winners: personal-stack wins for breadth and Spring Boot auto-config maturity; website may win parts of typed CommandBus and Vault JWT after compatibility tests. CommandBus adoption must account for personal-stack's two CommandBusConfig copies and three currently direct result-handler call sites.

Distribution: Maven packages on GitHub Packages under dev.extratoast. Each Spring add-on owns its own AutoConfiguration.imports metadata.

Consumption: personal-stack replaces libs:kotlin-common dependencies per service; website replaces its small vault lib with shared vault/vault-jwt and adopts command APIs only after tests. Dockerfiles must stop copying workspace libs/kotlin-common and must be able to resolve packages inside build stages.

Motivation: largest duplication/alignment win, especially command bus, Vault, web exceptions, timing, observability, and architecture tests. Effort XL, risk high.

### ExtraToast/vue-web-commons
Purpose: npm package for framework-neutral Vue utilities and optionally the existing personal-stack component exports if personal-stack adopts without prior local component migration.

Move: libs/vue-common utilities, composables, types, JWT helpers, Faro bootstrap, form/mutation/toast helpers, and tests. The current package is private, exports raw src/index.ts and SFC source, and treats Vue/Pinia/router/Faro as normal dependencies; it needs build output, dist exports, generated declarations, publishConfig, and peerDependencies. useAuth and useTheme are not generic until auth routes, role mapping, CSRF cookie, storage keys, and theme assumptions are injected.

Distribution: npm package on GitHub Packages under @extratoast.

Consumption: personal-stack UI packages replace @personal-stack/vue-common workspace dependency with @extratoast/vue-web-commons, update imports, theme CSS import, pnpm lock/workspace, and UI Dockerfiles. Website adoption waits for local evidence and Yarn Berry registry/PnP auth.

Motivation: shared frontend auth/API/error/observability behavior without forcing PrimeVue/Tailwind or Vuetify convergence. Effort M/L, risk medium.

### ExtraToast/openapi-client-gradle and ExtraToast/api-contract-checks
Purpose: split external client generation from internal service contract validation.

Move: website-style external spec/client generation from libs/openapi-specs and openapi-client conventions after local verification into openapi-client-gradle. Move personal-stack assistant-api/knowledge-api export tasks, assistant-ui openapi-typescript contract check pattern, package scripts, contract-banner.mjs behavior, and contract-validate workflow logic into api-contract-checks.

Distribution: Maven Gradle plugin for client generation; reusable scripts/actions or npm/CLI package for contract checks as appropriate.

Consumption: personal-stack updates service export tasks, assistant-ui scripts, package.json, pnpm lock, and contract-validate workflow. Website updates external client conventions and generated-output policy.

Motivation: avoids conflating Java external clients with TS contract drift checks while sharing the repeatable parts. Effort M, risk medium.

### ExtraToast/agent-kit
Purpose: pinned renderer/installer kit for Claude and Codex parity.

Move: platform/agents/kit renderer/templates/manifest behavior and platform/agents/council source, but preserve render-agent-kit.py --check, --write, --output, and --doctor. The extraction must include the knowledge-api installer serving model: InstallerController serves ClassPathResource installer/install.sh and substitutes @VERSION@ and @KB_URL@; platform/tooling tests assert manifest entries and /install.sh Flux routes.

Distribution: pinned release tarball or CLI, with checked-in generated resource or build-time vendoring chosen explicitly.

Consumption: personal-stack keeps CI that fails if .claude, .codex, .agents, services/knowledge-api installer resource, and agent-runner entrypoint drift from the same pinned kit. Website becomes a pure consumer after local verification.

Motivation: this is already a product installed into multiple repos; extraction prevents agent tooling drift. Effort M/L, risk medium-high due runtime serving and parity tests.

### ExtraToast/stalwart-provisioner
Purpose: schema-driven mail account/DKIM/provisioning tool.

Move: infra/stalwart Dockerfile, apply.sh, bootstrap.sh, accounts.json, plan.ndjson.tmpl, plan.dev.ndjson, dev fixtures, schema.min.json, and validate-plan.py patterns; website equivalents after local verification. Use a v2 schema with passwordRef union supporting env vars, mounted files, and Vault/VSO-backed files.

Distribution: GHCR image plus validation CLI.

Consumption: both repos replace local provisioning logic with schema + pinned image/CLI while keeping domain-specific accounts and DNS local.

Motivation: near-duplicate provisioning with meaningful credential-mode divergence; schema removes repeated shell logic. Effort M, risk medium.

### ExtraToast/homelab-platform-blueprints
Purpose: reusable NixOS/k3s/Flux blueprints and bootstrap helpers.

Move: only small reusable module subsets and generic bootstrap/validate scripts after local website verification. Keep personal-stack host data, secrets, app manifests, Nomad/Consul production jobs, platform/inventory/fleet.yaml, platform/scripts/render, and generated Flux outputs local until deploy-config-schema is ready.

Distribution: Nix flake input plus template/CLI.

Motivation: shares server setup without force-merging orchestration differences. Effort L, risk high.

### ExtraToast/deploy-config-schema
Purpose: late design/build of JSON-schema-driven deploy/infra configuration.

Move/design from personal-stack platform/inventory/fleet.yaml, platform/tooling, platform/scripts/render, and verified website Flux ingress/app samples. Generate Traefik, Gatus, catalog, Keel/image metadata, and later Nomad job inputs through adapters.

Distribution: schema + CLI + tests.

Motivation: explicit author goal, but aspirational and coupled. Effort L, risk high. Run after platform boundaries are proven.

### ExtraToast/authz-model
Purpose: shared schema/generated constants/evaluator contracts for roles, claims, permissions, groups, and host gates.

Move: no implementation initially. Study personal-stack ServicePermission, Role, AuthVerificationController, migrations, tests, and UI permission editing plus website auth locally. Do not merge auth services, OAuth client registrations, redirect URIs, or domain permission evaluators.

Distribution: Maven/TypeScript generated constants only after design.

Motivation: aligns permission vocabulary without baking one site's domain into the other. Effort L, risk high.

## Ordering

Hard gates first: local website inventory, package/action access, and container-stage auth. Then publish foundational shared repos: Gradle conventions and GitHub workflows. Then Kotlin/Spring commons, Vue commons, OpenAPI tooling, agent kit, and Stalwart. Consumer rewiring is serialized per repo wherever files overlap: settings.gradle.kts, gradle.properties, root package files, lockfiles, Dockerfiles, workflows, and service build files are first-class conflict nodes. Platform blueprints, deploy schema, and authz model come last.
