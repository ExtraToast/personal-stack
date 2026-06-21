# Tasks: Agent credential login portal

**Input**: [spec.md](./spec.md), [plan.md](./plan.md)

## Format

- **[P]**: can run in parallel (touches different files)
- Each task maps to one reviewable, revertable, stacked PR

## Phase 1 — Foundations (parallel)

- [x] T1 [P] Spec Kit artifacts (this directory)
- [x] T3 [P] `AGENTS_LOGIN` service permission in `auth-api` (+ tests)
- [x] T4 [P] `agents-login` MyApps card + placeholder icon in `app-ui`
- [x] T7 [P] Vault RBAC: narrow `agents-api`, add `agents-oauth-writer`, validate
- [x] T9 [P] VSO projections `claude-oauth-vss` / `codex-oauth-vss`
- [ ] T2 [P] Docs rewrite: stopgap + portal flow (`SETUP.md`, `README.md`,
      `AGENT-PARITY.md`)

## Phase 2 — Service

- [ ] T5 Standalone `services/agents-login/` Node service (controller + worker,
      PTY login, Vault CAS writeback, ≥80% coverage, Dockerfile with pinned CLIs)
- [ ] T6 Wire `agents-login` into CI + image publishing (depends on T5)

## Phase 3 — Deploy

- [ ] T8 Portal manifests: controller + worker Deployments/Services,
      `agents-login-worker` SA + Lease Role/RoleBinding, NetworkPolicies
- [ ] T10 Expose via `fleet.yaml` + render all five scripts (depends on T3, T8)

## Phase 4 — Deferred (blocked on companion runner PR + runtime gates)

- [ ] Vault↔PVC import/compare Job (seed Vault from PVCs, assert equality)
- [ ] Replace `refresh-ping` / `kb-install` / `auth-bootstrap` PVC mounts with
      the projected Secrets + init-copy, then drop their credential node pins
- [ ] Remove the credential justification from `AGENT_RUNTIME_NODE` (only after
      the orchestrator tolerates capability scheduling)
- [ ] Retire the `claude-credentials` / `codex-credentials` PVCs
- [ ] Optional long-lived credential keeper for refresh-token writeback

## Companion (agents repo, separate)

- [ ] Runner `Dockerfile` + `/opt/entrypoint.sh`: consume the projected Secrets
- [ ] `Fabric8AgentRunnerOrchestrator.kt`: swap PVC volume sources for Secret
      sources + init-copy

## Runtime gates (must resolve before runner cutover)

- [ ] Rotation-invalidation test with the exact pinned CLI versions
- [ ] Propagation measurement (token TTL vs Vault→VSO→kubelet→boot)
- [ ] Image-format parity across the three runner/login images
