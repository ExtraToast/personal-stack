# OTEL Observability Migration Plan

## Status

- Date: 2026-04-07
- State: In Progress
- Scope: Docker Compose and Nomad
- Goal: Move to a lighter, cleaner, more standard observability setup for Vue, Kotlin Spring Boot, and Python services
- Current phase: Phase 1
- Current step: Fix Grafana datasource wiring for Compose and make environment strategy explicit

## Why This Plan Exists

The current repo mixes three separate concerns:

- OTLP as the transport protocol
- collectors and agents as the ingestion layer
- Tempo, Loki, and Prometheus as storage/query backends

That has produced a few concrete problems:

- Applications export traces directly to Tempo instead of to a collector.
- Grafana datasource configuration is environment-sensitive and currently wrong for Docker Compose because it points to `127.0.0.1` inside the Grafana container.
- Promtail is past EOL and should not be the long-term log pipeline.
- The current setup makes it too easy to confuse "OTLP everywhere" with "best practice everywhere".

## Target Architecture

The target architecture for this repo should be:

- One Grafana Alloy instance per environment as the single OTLP ingest point.
- All server applications send OTLP traces to Alloy.
- Server logs stay as structured JSON on stdout/stderr and are collected by Alloy, not pushed directly by apps.
- Prometheus remains the primary metrics backend and keeps scraping application and infrastructure endpoints.
- Tempo stores traces.
- Loki stores logs.
- Grafana queries Prometheus, Loki, and Tempo only.

This is the recommended default for this repo because it is lighter and more mature than forcing all three signals through per-app OTLP exporters.

## Default Signal Policy

### Kotlin / Spring Boot

- Use the OpenTelemetry Java agent for tracing.
- Use structured JSON application logs with trace and span correlation fields.
- Use Actuator and Micrometer Prometheus endpoints for metrics.

### Python

- Use OpenTelemetry tracing.
- Use structured JSON logs with trace and span correlation fields.
- Use Prometheus metrics by default unless a strong need for OTLP metrics appears later.

### Vue Browser

- Start with tracing only.
- Use conservative sampling.
- Do not start with browser OTLP logs.
- Do not start with browser OTLP metrics.

## Non-Goals For The First Migration

- Do not make every app export metrics directly over OTLP in phase 1.
- Do not make every app export logs directly over OTLP in phase 1.
- Do not put Grafana in the ingestion path.
- Do not keep direct app-to-Tempo exports once Alloy is in place.

## Key Repo Problems To Fix

1. Grafana datasource wiring is wrong in Docker Compose.
   The current datasource file uses `127.0.0.1` for Prometheus, Loki, and Tempo. In a container this means "Grafana itself", not the sibling service containers.

2. Direct exporter topology is fragile.
   `auth-api`, `assistant-api`, and Traefik should not need backend-specific exporter knowledge.

3. Promtail needs to be retired.
   It is not the right long-term collector for this stack anymore.

4. Signal ownership needs to be simplified.
   - traces -> Tempo
   - logs -> Loki
   - metrics -> Prometheus
   - OTLP -> collector ingress, not backend identity

## Migration Principles

- Change one architectural concern at a time.
- Keep Docker Compose and Nomad in parity, but migrate Compose first.
- Prefer small, reviewable commits with one coherent concern each.
- Do not remove the existing path until the replacement path is verified.
- Update this file during every implementation step.
- Treat this file as the source of truth for status, decisions, learnings, and commit history.

## Required Discipline While Implementing

### Before Starting Any Step

- Re-read this file from top to bottom.
- Confirm the current step still matches reality.
- Update this file first if scope, sequence, or assumptions changed.
- Confirm which files are expected to change in this step.
- Confirm what the rollback point is.

### During The Step

- Keep changes limited to the stated scope.
- Note any surprise, mismatch, or newly discovered dependency immediately in this file.
- Do not accumulate unrelated changes into the same commit.

### Before Creating A Commit

- Double-check this file for consistency.
- Ensure the step status, changed files, verification status, and learnings are updated.
- Ensure the commit only contains one logical unit of work.
- Ensure follow-up work is recorded here instead of hidden in memory.

### After Creating A Commit

- Add the commit hash and summary to the commit log in this file.
- Add what was learned in the learning log.
- Mark the step as completed, blocked, or partially completed.
- Record any deviation from the original plan.

## Concrete Migration Sequence

## Phase 0: Lock The Architecture Decision

### Goal

Commit to the default architecture before changing any code.

### Decision

- Use Alloy as the single ingest layer.
- Keep Prometheus scraping for metrics.
- Use Alloy to collect logs and route them to Loki.
- Use OTLP traces from applications to Alloy, then Alloy to Tempo.

### Deliverables

- This plan file exists and is agreed.
- The team explicitly rejects "OTLP everything immediately" as the first migration target.

### Files To Expect Later

- `docker-compose.yml`
- `infra/observability/grafana/provisioning/datasources/datasources.yaml`
- `infra/traefik/traefik-dev.yml`
- `infra/nomad/templates/traefik-static.yml.tpl`
- `infra/observability/promtail/promtail.yaml`
- `infra/observability/tempo/tempo.yaml`
- new Alloy config files under `infra/observability/`
- new Nomad job files or templates for Alloy

### Verification

- The plan is specific enough that implementation steps can be executed without re-architecting in the middle.

## Phase 1: Fix Grafana Datasource Wiring First

### Goal

Make sure observability data can be queried correctly before reworking ingestion.

### Why This Comes First

If Grafana cannot reach Tempo, Loki, and Prometheus correctly, traces may appear absent even when export works.

### Work

- Split or template Grafana datasource configuration for Docker Compose and Nomad.
- For Docker Compose, use service DNS names:
  - `prometheus:9090`
  - `loki:3100`
  - `tempo:3200`
- For Nomad host networking, keep `127.0.0.1` only where it is actually correct.
- Verify that Grafana can query all three backends in both environments.

### Files Likely In Scope

- `infra/observability/grafana/provisioning/datasources/datasources.yaml`
- `docker-compose.yml`
- any new environment-specific datasource file or template introduced to remove ambiguity

### Acceptance Criteria

- Grafana in Docker Compose can query Prometheus, Loki, and Tempo.
- Grafana in Nomad can query Prometheus, Loki, and Tempo.
- The datasource strategy is explicit and not environment-accidental.

### Suggested Commit Boundary

- Commit 1: fix Grafana datasource environment wiring only

## Phase 2: Introduce Alloy In Docker Compose

### Goal

Insert a collector layer between applications and backends in local development first.

### Work

- Add an Alloy service to Docker Compose.
- Create an Alloy config that includes:
  - OTLP receivers
  - batch processor
  - memory limiter
  - resource or attribute normalization where needed
  - OTLP exporter to Tempo for traces
  - log pipeline to Loki
- Point `auth-api`, `assistant-api`, and Traefik to Alloy instead of directly to Tempo.
- Keep the current backends in place behind Alloy.

### Files Likely In Scope

- `docker-compose.yml`
- new Alloy config file under `infra/observability/`
- `infra/traefik/traefik-dev.yml`

### Acceptance Criteria

- All server traces go to Alloy, not directly to Tempo.
- Alloy exports traces successfully to Tempo.
- Local trace visibility still works after the path change.

### Suggested Commit Boundaries

- Commit 2: add Alloy service and base config
- Commit 3: reroute local trace exporters from Tempo to Alloy

## Phase 3: Replace Promtail With Alloy Log Collection

### Goal

Move log collection to Alloy and remove Promtail from the long-term path.

### Work

- Configure Alloy to collect container logs or file logs used by local development.
- Preserve useful labels and service naming needed by Loki queries and Grafana dashboards.
- Keep log parsing simple and stable.
- Verify logs from major services appear in Loki through Alloy.
- Remove Promtail from Docker Compose after replacement is verified.

### Files Likely In Scope

- `docker-compose.yml`
- `infra/observability/promtail/promtail.yaml`
- new Alloy config file or Alloy log pipeline section

### Acceptance Criteria

- Logs from core services reach Loki through Alloy.
- Existing dashboards or log searches still work, or any query changes are documented.
- Promtail is removed from the active path.

### Suggested Commit Boundaries

- Commit 4: add Alloy log collection pipeline
- Commit 5: remove Promtail after verification

## Phase 4: Standardize Resource Attributes And Correlation

### Goal

Make all telemetry consistently searchable and correlatable.

### Work

- Standardize at least:
  - `service.name`
  - `service.version`
  - `deployment.environment`
- Confirm service names are stable across traces, logs, and metrics.
- Add or verify trace and span correlation fields in structured logs for Spring Boot and Python.
- Ensure dashboard and query conventions match the final naming.

### Files Likely In Scope

- app runtime env configuration
- logging configuration files
- deployment manifests or templates
- dashboards or datasource-derived field config if correlation field names change

### Acceptance Criteria

- A trace can be followed into logs.
- Service naming is consistent across signals.
- Resource metadata does not vary accidentally by environment.

### Suggested Commit Boundary

- Commit 6: standardize telemetry resource metadata and log correlation

## Phase 5: Keep Metrics Simple And Explicit

### Goal

Reduce confusion about metrics ownership and avoid premature OTLP metrics complexity.

### Work

- Keep Prometheus scraping application and infrastructure metrics.
- Keep `OTEL_METRICS_EXPORTER=none` on services unless a deliberate later decision changes that.
- Review Tempo metrics-generator usage and keep it only if service graphs and span metrics are actively useful.
- Document clearly that:
  - app metrics come from Prometheus scraping
  - trace-derived metrics may come from Tempo metrics-generator
  - OTLP metrics are not the default in this repo

### Files Likely In Scope

- `infra/observability/prometheus/prometheus.dev.yml`
- `infra/observability/prometheus/prometheus.yml`
- `infra/observability/tempo/tempo.yaml`
- docs that currently blur signal ownership

### Acceptance Criteria

- Metrics collection strategy is unambiguous.
- There is no expectation that every service must export OTLP metrics.

### Suggested Commit Boundary

- Commit 7: simplify and document metrics ownership

## Phase 6: Bring Nomad To The Same Model

### Goal

Replicate the Compose architecture in production-style deployment.

### Work

- Add an Alloy Nomad job.
- Wire Alloy to local host-mode endpoints as needed.
- Update application OTLP endpoints in Nomad jobs to point to Alloy instead of directly to Tempo.
- Update Traefik Nomad tracing config to point to Alloy.
- Ensure Grafana datasource wiring remains correct for host-mode deployment.

### Files Likely In Scope

- new `infra/nomad/jobs/observability/alloy.nomad.hcl`
- `infra/nomad/jobs/apps/auth-api.nomad.hcl`
- `infra/nomad/jobs/apps/assistant-api.nomad.hcl`
- `infra/nomad/templates/traefik-static.yml.tpl`
- any Nomad Alloy config templates

### Acceptance Criteria

- Nomad applications no longer export directly to Tempo.
- Nomad Traefik no longer exports directly to Tempo.
- Production-style deployment matches local architecture.

### Suggested Commit Boundaries

- Commit 8: add Alloy to Nomad
- Commit 9: reroute Nomad apps and Traefik to Alloy

## Phase 7: Add Frontend And Python Service Guidance

### Goal

Make future service onboarding consistent.

### Work

- Document a standard Vue browser tracing setup:
  - traces only
  - conservative sampling
  - context propagation requirements
  - backend CORS and header allowance if needed
- Document a standard Python service setup:
  - tracing enabled
  - structured logs with correlation
  - Prometheus metrics by default
- Document the standard Spring Boot setup:
  - Java agent for tracing
  - JSON logs
  - Prometheus scrape metrics

### Acceptance Criteria

- New services can follow one documented observability recipe per platform.

### Suggested Commit Boundary

- Commit 10: add platform onboarding guidance

## Phase 8: Optional Later Evaluation Of OTLP Logs And OTLP Metrics

### Goal

Treat "OTLP everything" as an explicit later decision, not an accidental first migration.

### Work

- Only evaluate after the default architecture is stable.
- If OTLP logs are considered, compare:
  - app-emitted OTLP logs
  - structured stdout logs collected by Alloy
- If OTLP metrics are considered, compare:
  - Prometheus scrape model
  - Prometheus OTLP receiver model
- Record operational tradeoffs before changing the default.

### Decision Gate

Do not proceed unless there is a concrete need that the default model does not satisfy.

### Acceptance Criteria

- Any move toward OTLP logs or OTLP metrics is deliberate, documented, and justified.

## Verification Checklist By Phase

### For Every Phase

- [ ] The changed scope matches the current phase only.
- [ ] This plan file was updated before the commit.
- [ ] This plan file was updated after verification.
- [ ] The commit log entry was added.
- [ ] A learning entry was added.

### Phase 1

- [ ] Grafana can query Tempo in Docker Compose.
- [ ] Grafana can query Loki in Docker Compose.
- [ ] Grafana can query Prometheus in Docker Compose.
- [ ] Grafana datasource behavior is correct in Nomad too.

### Phase 2

- [ ] Alloy receives traces from `auth-api`.
- [ ] Alloy receives traces from `assistant-api`.
- [ ] Alloy receives traces from Traefik if Traefik remains instrumented.
- [ ] Tempo stores traces forwarded by Alloy.

### Phase 3

- [ ] Alloy log collection reaches Loki.
- [ ] Core service logs remain searchable.
- [ ] Promtail is fully removed or clearly marked temporary.

### Phase 4

- [ ] `service.name` is stable across signals.
- [ ] `deployment.environment` is correct.
- [ ] Logs contain trace and span identifiers where expected.

### Phase 5

- [ ] Prometheus still scrapes application metrics.
- [ ] Prometheus still scrapes infrastructure metrics.
- [ ] Any Tempo-derived metrics are intentional and documented.

### Phase 6

- [ ] Nomad apps send traces to Alloy.
- [ ] Nomad Traefik sends traces to Alloy.
- [ ] Grafana still reaches all backends in Nomad.

## Commit Strategy

### Rules

- One logical concern per commit.
- Do not mix docs-only cleanup with runtime wiring unless the docs explain that exact wiring change.
- Do not mix Docker Compose and Nomad migrations in one commit unless the change is mechanically identical and low risk.
- Keep commit messages explicit.

### Suggested Commit Message Pattern

- `observability: fix grafana datasource wiring for compose`
- `observability: add alloy service and collector config`
- `observability: route app traces through alloy`
- `observability: migrate log collection from promtail to alloy`
- `observability: standardize telemetry resource attributes`
- `observability: align nomad tracing with alloy collector`

## Commit Log

Update this table after every commit during implementation.

| Status  | Commit | Scope   | What Is In The Commit                                                            | Follow-Up                                     |
| ------- | ------ | ------- | -------------------------------------------------------------------------------- | --------------------------------------------- |
| planned | n/a    | Phase 1 | Fix Grafana datasource wiring for Compose and make environment strategy explicit | Verify both Compose and Nomad                 |
| planned | n/a    | Phase 2 | Add Alloy service and base collector config for local development                | Validate receiver/exporter path               |
| planned | n/a    | Phase 2 | Reroute local traces from apps and Traefik to Alloy                              | Check trace search in Grafana                 |
| planned | n/a    | Phase 3 | Add Alloy log collection pipeline                                                | Confirm Loki labels and search behavior       |
| planned | n/a    | Phase 3 | Remove Promtail from active local path                                           | Verify nothing still depends on Promtail      |
| planned | n/a    | Phase 4 | Standardize resource metadata and log correlation fields                         | Recheck dashboards and queries                |
| planned | n/a    | Phase 5 | Clarify and simplify metrics ownership                                           | Decide whether Tempo metrics-generator stays  |
| planned | n/a    | Phase 6 | Add Nomad Alloy deployment and reroute Nomad exporters                           | Verify host-mode networking assumptions       |
| planned | n/a    | Phase 7 | Add Vue, Spring Boot, and Python onboarding guidance                             | Keep guidance aligned with final architecture |

## Step Log

Update this section during implementation. Add one subsection per implementation step.

### Template

#### Step X

- Date:
- Goal:
- Files changed:
- Verification performed:
- Commit:
- Result:
- Follow-up:

### Active Step Entries

#### Step 1

- Date: 2026-04-07
- Goal: Fix Grafana datasource wiring for Docker Compose, keep Nomad correct, and make the environment split explicit in repo configuration.
- Files changed:
  - `docs/private/otel-observability-migration-plan.md`
  - `docker-compose.yml`
  - `infra/nomad/jobs/observability/grafana.nomad.hcl`
  - `infra/observability/grafana/provisioning/datasources/datasources.compose.yaml`
  - `infra/observability/grafana/provisioning/datasources/datasources.nomad.yaml`
  - removed `infra/observability/grafana/provisioning/datasources/datasources.yaml`
- Verification performed:
  - Re-read this plan before starting implementation.
  - Confirmed current Grafana datasource config incorrectly uses `127.0.0.1` for Docker Compose.
  - Checked all datasource file references after the split.
  - Ran `docker compose -f docker-compose.yml config`.
  - Ran `nomad job validate infra/nomad/jobs/observability/grafana.nomad.hcl`.
- Commit:
  - Not created yet.
- Result:
  - Implementation complete.
  - Static validation complete.
  - Runtime verification still pending because the relevant services are not running in this workspace.
- Follow-up:
  - Create the phase 1 commit.
  - Verify Grafana queries against live backends when the environments are running.

## Learning Log

Every implementation step must add at least one learning entry here. Keep the wording concrete and operational.

### Template

- Date:
- Step:
- Learned:
- Impact:
- Action taken:

### Active Learning Entries

- Date: 2026-04-07
- Step: 1
- Learned: The current Grafana datasource provisioning file is implicitly Nomad-oriented and therefore wrong for Docker Compose because `127.0.0.1` resolves inside the Grafana container.
- Impact: Query failures can make traces, logs, and metrics appear absent even when ingestion works.
- Action taken: Split the datasource provisioning into explicit Compose and Nomad files and updated each runtime to mount the correct one.

## Double-Check Checklist For This File

Run this checklist before and after every implementation step.

- [ ] The current phase is marked accurately.
- [ ] Any changed sequence or scope is reflected here.
- [ ] The commit log is up to date.
- [ ] The step log is up to date.
- [ ] The learning log is up to date.
- [ ] Acceptance criteria for the current step are still correct.
- [ ] Open questions are still visible and not hidden in code comments or memory.

## Open Questions To Resolve During Implementation

- Should Traefik tracing remain enabled in both environments, or only where it provides real value?
- Should Tempo metrics-generator remain enabled after the stack is simplified, or is it extra cost without enough dashboard value?
- What exact log format and field names should be standardized for Spring Boot and Python correlation?
- Should Alloy own any Prometheus scrape orchestration later, or should Prometheus remain fully independent?

## Source Notes

These references informed the plan and should be rechecked if implementation reveals contradictions:

- OpenTelemetry Collector overview: <https://opentelemetry.io/docs/collector/>
- OpenTelemetry Java agent: <https://opentelemetry.io/docs/zero-code/java/agent/>
- OpenTelemetry JavaScript status: <https://opentelemetry.io/docs/languages/js/>
- OpenTelemetry Python docs: <https://opentelemetry.io/docs/languages/python/>
- Grafana Alloy for application observability: <https://grafana.com/docs/opentelemetry/collector/grafana-alloy/>
- Loki / Promtail docs, including Promtail deprecation and EOL notice: <https://grafana.com/docs/loki/latest/send-data/promtail/>
- Prometheus OTLP guide and caveats: <https://prometheus.io/docs/guides/opentelemetry/>
