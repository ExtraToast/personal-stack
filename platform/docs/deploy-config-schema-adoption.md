# Deploy Config Schema Adoption

## Current State

personal-stack currently treats `platform/inventory/fleet.yaml` as the source of truth for host, placement, exposure, access, ingress, and monitoring intent. The Kotlin platform tooling loads that file and renders the committed edge artifacts through `platform/scripts/render/*`:

- `platform/cluster/flux/apps/edge/edge-catalog-configmap.yaml`
- `platform/cluster/flux/apps/edge/edge-route-catalog-configmap.yaml`
- `platform/cluster/flux/apps/edge/traefik-ingressroutes.yaml`
- `platform/cluster/flux/apps/edge/traefik-lan-ingressroutes.yaml`
- `platform/cluster/flux/apps/observability/gatus/gatus-endpoints-configmap.yaml`

`platform/scripts/validate/render-platform.sh` remains the live gate for those committed outputs. This adoption step does not replace the Kotlin renderer, render scripts, Flux tree, Nix hosts, or live manifests.

## Toolkit Fit

`@extratoast/deploy-config-schema` v0.3.0 can validate the canonical `deploy-config` contract and has adapters for the same first edge surfaces personal-stack already renders: `edge-catalog`, `edge-route-catalog`, `gatus`, `traefik-public`, and `traefik-lan`.

The toolkit deploy-config shape is close to personal-stack's current fleet model, but it is not identical. The first safe consumption point is therefore validation of a generated, schema-only deploy-config derived from `fleet.yaml`, not direct renderer replacement.

## Parity Gaps

- Input shape: personal-stack's live `fleet.yaml` has `placement_intent.frankfurt_only`, `placement_intent.enschede_only`, and `placement_intent.gpu_specific`; the toolkit deploy-config schema expects `site_affinity`, `node_affinity`, and `gpu_preferences`.
- Required deploy-config metadata: the toolkit schema requires `ingress_intent.defaults`, `ingress_intent.route_rules`, `image_metadata`, and `adapter_output_intent`; these are implicit or absent in `fleet.yaml`.
- Dynamic workloads: `agent-runner` appears in placement intent but is created dynamically by assistant-api rather than declared as a static service, so the generated schema input omits it from deploy-config placement mappings.
- Edge route catalog field name: the Kotlin catalog emits `excluded_paths`; the toolkit route rule/schema uses `excluded_exact_paths`.
- Route special cases: personal-stack has stack-specific route splitting for auth, assistant, and knowledge-api bearer-token paths. The toolkit can model these with data-driven `route_rules`, but `fleet.yaml` does not yet store those rules directly.
- Edge YAML formatting: Kotlin/Jackson emits document starts and quoted scalar strings inside ConfigMap data. The toolkit uses the `yaml` package and may produce semantically equivalent but byte-different YAML.
- Full Kubernetes app manifests: existing Flux apps contain hand-tuned Vault Agent injection templates, multi-container pods, Java/OTel/Pyroscope settings, CRaC details, backup jobs, Grafana dashboards, HelmRelease values, ServiceMonitors, PDBs, and app-specific comments. The toolkit Kubernetes adapter is generic and cannot be considered equivalent yet.
- Flux layering: personal-stack's Flux root has explicit readiness dependencies and health checks for CRD-owning layers such as observability and Grafana dashboards. The toolkit Flux adapters have similar concepts, but their generated layer graph and values need diff review before use.
- Nix hosts: personal-stack's `platform/flake.nix` includes deploy-rs details, Pi SD-image outputs, disko subsets, magic rollback exceptions, nixos-anywhere, and host-specific module patterns. The toolkit `nix-hosts` adapter emits a simpler platform-blueprints-based tree and is not a drop-in replacement.
- Secrets/VSO: personal-stack has bespoke Vault bootstrap, Vault Agent, VSO, and static secret references. The toolkit VSO adapter is reference-only and intentionally does not render seed data, Vault policies, dynamic database templates, or secret values.

## Adopted First Step

This branch consumes `@extratoast/deploy-config-schema` as a pinned devDependency and adds an additive CI validation job:

1. Generate a temporary deploy-config JSON from `platform/inventory/fleet.yaml`.
2. Validate that generated deploy-config with `deploy-config-schema validate deploy-config`.
3. Leave all existing Gradle, platform render, system-test, and live manifest jobs unchanged.

The generated deploy-config is not committed and is not applied to the cluster.

A report-only drift helper is available at `pnpm deploy-config:schema:drift`. In the current assessment, toolkit `traefik-public` and `traefik-lan` output matched the committed Kotlin-rendered files byte-for-byte. `edge-catalog`, `edge-route-catalog`, and `gatus` differed in ConfigMap data formatting, and `edge-route-catalog` also exposes the `excluded_paths` versus `excluded_exact_paths` contract difference.

## Incremental Path

1. Keep the validation job green while evolving `fleet.yaml` toward the toolkit contract.
2. Move implicit route defaults and special cases into declarative input fields so the adapter script gets thinner.
3. Add a non-gating drift report that renders toolkit edge outputs into `/tmp` and compares them with the committed Kotlin-rendered edge artifacts.
4. Normalize known harmless YAML formatting differences, then promote edge drift from report-only to gating.
5. Replace one edge renderer at a time only after byte-level or explicitly normalized semantic parity is proven.
6. Assess Flux/Kubernetes/Nix adapters separately with generated output in an isolated directory; do not point them at live paths until their diffs are reviewed and risk-bounded.
7. Retire Kotlin renderer code only after all replacement renderers have stable gating, rollback instructions, and at least one clean CI cycle.

## Risks

- A schema-valid generated deploy-config does not prove runtime correctness; it only proves that the current fleet can be mapped into the toolkit contract.
- Renderer replacement before route parity would risk auth bypasses, broken health exemptions, or incorrect SSO middleware attachment.
- Renderer replacement before WAN-origin parity would risk Cloudflare proxy mode or DNS target regressions for WebSocket and home media routes.
- Full tree generation could overwrite hand-tuned Flux, Vault, Nix, and app manifests if adopted directly. All future render tests must write to scratch paths until explicitly promoted.
