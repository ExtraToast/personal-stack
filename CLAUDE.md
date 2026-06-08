# personal-stack — operating guide for Claude

Personal homelab GitOps monorepo: k3s on NixOS across a Frankfurt VPS +
Enschede home nodes, managed via Flux CD. The user is an experienced
platform operator running ambitious multi-component changes end-to-end.

## How to collaborate

- **Validate, don't hand-wave.** If you don't know, grep the source,
  check upstream docs, or run the command. "I think" without evidence
  is not acceptable. Past correction: "Stop circle jerking, if you
  don't know something google it. Anything you try must be validated."
- **Check current state before designing new shapes.** Before
  introducing a new path, secret name, schema, or resource, grep the
  repo *and* ask about live state (Vault paths, K8s resources,
  existing config). Past incident: invented
  `secret/platform/edge/cloudflare.api_token` when the user already
  stored it at `secret/platform/edge.cloudflare.dns_api_token`. The
  fix is cheaper before the PR than after.
- **Flag fundamental limitations upfront.** When an approach has
  known architectural friction (reverse-proxying a complex web UI,
  browser Private-Network-Access blocks, forward-auth vs CLI, etc.),
  say so and offer the native alternative *before* the first
  implementation attempt. Don't ship a brittle proxy then pile hacks
  on when it breaks — name the escape hatch up front and let the user
  choose.
- **Triage with fixability weighting.** When multiple things are
  broken, produce a ranked table with columns for impact and
  actionability (code-only PR vs needs cluster state / user input).
  User's phrasing: "prioritize things that can be fixed."
- **Finish the job autonomously.** Given work, run it through: write,
  test, push, create PR, merge after CI, reconcile, verify. Don't stop
  at "here's a plan" unless explicitly asked for one.
- **Small stacked PRs.** Split work so each PR is reviewable and
  revertable alone. Stacking (PR B based on PR A) is normal. Never
  bundle unrelated fixes.
- **Commit every change immediately.** No uncommitted work left in
  tree beyond an active edit. Survives branch switches and IDE reverts.
- **Never clobber parallel edits.** If there are uncommitted changes
  in files you didn't touch, leave them alone. Stash only your own
  work, restore it to the right branch, never `git checkout .` or
  similar destructive shortcuts.
- **Prefer simplicity over flexibility.** If `sso_protected` means
  "whole host behind forward-auth", don't suggest `/api/` whitelists.
  Match the mental model, don't expand it.
- **No tangential cleanup.** Bug fixes don't need refactors. Don't add
  comments explaining WHAT the code does — only WHY, only when the why
  is non-obvious. No multi-paragraph docstrings.
- **Auto mode is the default posture.** Proceed on reasonable
  assumptions; ask only before destructive or shared-state actions.
- **KB-first, but bounded.** Before designing repo/cluster/agent
  changes, call `knowledge.recall` with a distilled query,
  `scope=project:personal-stack`, `mode=hybrid`, and `limit <= 5`.
  Start from snippets, then relations, then full notes only when
  needed. At the end, capture durable lessons or decisions to the KB.
  Never paste broad KB dumps or full transcripts into the prompt.
- **Claude/Codex parity is mandatory.** Any agent skill, hook, memory
  rule, or installer behavior added for Codex must get the Claude
  equivalent in the same branch, and vice versa. Codex-only `.agents` or
  `.codex` changes are incomplete until `.claude`, `CLAUDE.md`, or the
  installer carries the matching behavior.

## Repo layout

- `services/` — JVM services (Kotlin, Gradle) + Vue/TS UIs.
  `auth-api`, `assistant-api`, `app-ui`, `auth-ui`, `assistant-ui`,
  `system-tests` (Playwright).
- `platform/`
  - `inventory/fleet.yaml` — declarative source of truth: nodes,
    services, exposure, access, ingress intent. Rendering turns this
    into Traefik IngressRoutes + catalog ConfigMaps.
  - `cluster/flux/apps/` — Flux-managed manifests grouped by domain
    (edge, core, utility-system, media-system, data, observability …).
  - `scripts/render/` — four shell wrappers that regenerate YAML.
    Always run after touching `fleet.yaml`, then commit everything.
  - `tooling/` — Kotlin renderer + validator (`:platform:tooling`).
    Unit tests gate `fleet.yaml` changes.
  - `nix/` — flake, hosts, modules, profiles for NixOS nodes.
- `infra/scripts/` — ops scripts (`make-admin.sh`, etc.).

## Render + validate (run after every fleet.yaml change)

```bash
platform/scripts/render/render-edge-catalog-configmap.sh
platform/scripts/render/render-edge-route-catalog-configmap.sh
platform/scripts/render/render-traefik-ingressroutes.sh
platform/scripts/render/render-traefik-lan-ingressroutes.sh
./gradlew :platform:tooling:test
kubectl kustomize platform/cluster/flux/clusters/production > /dev/null
```

CI's "Platform Validate" job diffs the rendered ConfigMaps against the
committed tree, so rendering must happen locally before commit.

## Adding a public service — files that must stay aligned

1. `platform/inventory/fleet.yaml` — `service_intent.kubernetes.public_apps`,
   `placement_intent.*`, `exposure_intent.*`, `access_intent.sso_protected`,
   `access_intent.host_labels`, `ingress_intent.kubernetes_backends`.
2. `services/auth-api/src/main/kotlin/.../domain/model/ServicePermission.kt`
   — enum entry with subdomain.
3. `services/auth-api/src/test/kotlin/.../ServicePermissionTest.kt`
   — `@CsvSource` row.
4. `services/app-ui/src/features/apps/data/serviceRegistry.ts` +
   `services/app-ui/public/icons/<name>.svg` — MyApps card.
5. `infra/scripts/make-admin.sh` — grant row (optional; ADMIN bypasses).

## Patterns worth remembering

- **CRaC training is shared in `libs/kotlin-common`.** Every Spring
  service in this monorepo opts in to JIT-warmup-then-checkpoint by
  enabling `crac.train.enabled=true` (typically via the `crac-train`
  Spring profile) and listing its hot endpoints in
  `application-crac-train.yml`. The shared `CracTrainingRunner`
  (auto-configured via `CracAutoConfiguration` in kotlin-common) hits
  each endpoint locally `iterations` times after `ApplicationReadyEvent`,
  then calls `org.crac.Core.checkpointRestore()`. The image build /
  CI / k8s caps that take advantage of the resulting checkpoint live
  in each service's Dockerfile + deploy.yml; the runner itself is a
  no-op in production because the property defaults to `false`.
- **Host-native services exposed as k8s backends.** Host-native daemons
  (AdGuard on t1000, the ASUS router) get a selector-less Service +
  Endpoints (or a tiny relay Deployment) in
  `utility-system/<name>-gateway/` so Traefik routes to them like any
  other k8s backend.
- **Node pinning via capability labels.** Use
  `personal-stack/capability-lan-ingress: 'true'` for pods needing LAN
  egress, `capability-nvidia: 'true'` for GPU work.
- **NVIDIA on NixOS.** nvidia-device-plugin uses the `nvidia-cdi`
  RuntimeClass. Config: `deviceListStrategy: envvar`,
  `deviceIDStrategy: index`. Let k3s auto-detect containerd config
  rather than hand-writing the template.
  `pkgs.nvidia-container-toolkit.tools` + `pkgs.runc` must be on the
  k3s systemd PATH.
- **Gluetun VPN pod** (qbittorrent/prowlarr) needs
  `FIREWALL_OUTBOUND_SUBNETS` including cluster CIDRs
  (`10.42.0.0/16,10.43.0.0/16`), `DOT=off`, `DNS_KEEP_NAMESERVER=on` —
  otherwise siblings + DNS break.
- **Keel image automation.** `keel.sh/policy: force` MUST be paired
  with `keel.sh/match-tag: "true"` or Keel pins to the first SHA it
  sees. Don't omit `match-tag`.
- **In-cluster service URLs** look like
  `http://<service>.<namespace>.svc.cluster.local:<port>`. Prefer these
  over public hosts for service-to-service — they skip Traefik and
  forward-auth entirely.
- **Forward-auth blocks CLI tools.** Every `sso_protected` public host
  (`vault.*`, etc.) 302s unauthenticated requests to the auth-ui login
  page. CLIs don't do cookies, so `vault login`, raw `curl`, etc.
  get "nil response from pre-flight request" or similar. The standard
  workaround is `kubectl port-forward -n <ns> svc/<svc> <port>:<port>`
  and set `VAULT_ADDR=http://127.0.0.1:<port>` (or equivalent). Don't
  carve path-exceptions into the public ingress unless there's a
  genuine need — the port-forward is cheap.
- **Tailscale subnet routing is the escape hatch for LAN-only admin
  UIs.** For things that fight reverse-proxying (ASUS router UI,
  anything with hard-coded LAN IPs in client-side JS, browser PNA
  blocks), don't pile nginx `sub_filter` rewrites. Instead advertise
  the LAN subnet from an Enschede node via
  `services.tailscale.extraUpFlags = [ "--advertise-routes=<cidr>" ]`,
  approve in the Tailscale admin console, and use the native LAN IP
  from the tailnet. Reverse-proxy + forward-auth stays for things that
  actually cooperate with it.
- **Flannel runs over `tailscale0`.** `--flannel-iface=tailscale0` on
  all nodes means pod-to-pod cross-site traffic is already tunneled via
  the tailnet. For *host-native* services (AdGuard, etc.) exposed as
  selector-less Endpoints pointing at a host's tailnet IP, Traefik-pod
  → host-IP takes a different iptables path than pod-to-pod and can
  misbehave with SNAT. When that happens, convert the daemon to a real
  k8s Deployment (nodeSelector + hostPort if it needs privileged host
  ports) rather than debugging host-iptables on Frankfurt.

- **Knowledge-vault structure.** Captures land in
  `_inbox/<day>/<time>-<slug>--<id8>.md` (worker). The
  `knowledge-curator` CronJob runs twice daily (09:00 & 18:00
  Europe/Amsterdam); it classifies via Ollama with
  JSON-schema-constrained output, validates `topic:<slug>` against
  `topics.yaml`, and
  `git mv`s the file to `topics/<topic-slug>/<type>/<slug>.md` or
  `projects/<github-repo-name>/<type>/<slug>.md`. Unclassifiable
  notes go to `_inbox/_needs-review/`; low-value captures are
  discarded to `_inbox/_discarded/`, and stuck review files
  auto-escalate to discard after 3 attempts. After any pass that promotes
  at least one note the curator regenerates `_index/recent.md`,
  per-topic MoCs at `_index/topics/<slug>.md`, and
  `_index/conflicts.md`. Full design in
  `docs/private/knowledge-vault-redesign.md`.
- **LightRAG retrieval.** Deployed at
  `lightrag.knowledge-system.svc.cluster.local:9621`. Backend:
  pgvector KV/vector on the same `knowledge_db`, NetworkX disk-
  backed graph in its own 2 GiB PVC. Pinned to
  `ghcr.io/hkuds/lightrag:v1.4.16` — bump deliberately via a
  PR; the v1.4.9 → next migration once cost a 17-hour downtime
  (LightRAG issue #2255). Uses in-cluster Ollama via
  `/v1/chat/completions` for extraction + mix-mode generation;
  pin Ollama outside `0.13.0-0.13.2` (LightRAG #2495 broke the
  embedding path on those versions). The curator publishes every
  promoted note via `POST /documents/text` fire-and-forget — a
  slow LightRAG never blocks git + DB writes.
- **Topic vocabulary is closed.** Add a topic = edit
  `platform/cluster/flux/apps/knowledge/knowledge-curator/topics-configmap.yaml`
  in a deliberate PR. The classifier rejects `topic:<slug>` values
  that aren't in the list and routes the note to
  `_inbox/_needs-review/`. This prevents the LLM from forking
  `kotlin` / `Kotlin` / `kt` into three folders.

## Quick test commands

```bash
./gradlew :services:auth-api:test            # unit + integration (Testcontainers)
./gradlew :services:auth-api:ktlintCheck     # lint
./gradlew :platform:tooling:test             # renderer + inventory
(cd services/app-ui && npm run typecheck && npm run lint)
```

## Ops primitives

- Cluster: `deploy@167.86.79.203:2222`, repo at `/opt/personal-stack`.
  Flux reconciles from `main`.
- Keel polls ghcr every 2 min for in-house `:latest` images and rolls
  the matching Deployment.
- Standard ops: `gh pr create …`,
  `flux reconcile kustomization <name> --timeout=60s`,
  `kubectl rollout restart deploy <name>`.

## PR conventions

Every PR opened via `gh pr create` must be:

- **Assigned to `ExtraToast`** — `--assignee ExtraToast`.
- **Labelled** — `--label <label>` with the matching repo label(s):
  - `enhancement` — new feature, new service, new capability.
  - `bug` — fixes broken behaviour.
  - `documentation` — doc / CLAUDE.md / README changes only.
  - `dependencies` + `docker` / `java` / `javascript` /
    `github_actions` — version bumps (usually dependabot territory,
    but match the pattern if you open one by hand).
  - Prefer a single best-fit label; stack multiple only when the PR
    genuinely spans categories.

### Voice & phrasing in PR bodies and commit messages

The PR body and commit message are authored by the human driver, not
by an assistant addressing the driver — match that voice.

- **No second-person pronouns** ("you", "your", "you asked for", "as
  you can see"). The reader is whoever is reviewing the diff; the
  author is whoever opened the PR. Neither needs to be named.
- **No first-person plural narration of intent** ("we now do X", "we
  wanted Y"). State the change and the reason directly: "X now does
  Y because Z" — no pronouns required.
- **Prefer impersonal, professional prose.** Lead with the
  observable behaviour or the root cause; follow with the change.
  Examples:
  - Bad: "You were seeing /me take 6 s, so I added an aspect that
    traces every method, this way you can see what's slow."
  - Good: "`/me` was taking ~6 s end-to-end with no visibility past
    the JDBC SELECT. A Micrometer-based AOP aspect now wraps every
    `@Service` / `@Repository` / `@Controller` method, producing
    one span per call so the silent windows show up in Tempo."
- **Past tense for state at the time the PR was opened, present /
  future tense for the change.** "Span volume was dominated by
  health probes; `otelcol.processor.filter` now drops them at
  Alloy."
- **No conversational hedging** ("hopefully", "should be fine",
  "I think"). State what the change does and what proves it works.
- **Rephrase rather than paraphrase.** If a draft started as a
  dialogue with the user, rewrite the body from scratch in the
  PR-body voice — do not patch pronouns one at a time.

Apply the same rules to commit messages.

Default template:
```bash
gh pr create --assignee ExtraToast --label enhancement \
  --title "..." --body "$(cat <<'EOF'
...
EOF
)"
```

## What NOT to do

- **Never add `Co-Authored-By` trailers to commits or PR bodies.** No
  "Generated with Claude Code" footers either. Commits are authored
  solely by the human driver.
- **Never include the name `Claude` anywhere in code, comments, commit
  messages, PR bodies, generated files, or operational notes unless the
  user explicitly asks for that text.** Do not add co-author,
  generated-by, or assistant attribution in any form.
- **Don't auto-fetch external assets** (icons, fonts, scripts, images)
  into the repo without explicit authorization. The user will either
  provide the file or approve the source. Hand-crafted placeholders
  are fine as a default; the user can swap them later.
- **Never escape backticks in `gh pr create --body` / `--body-file`
  content.** The heredoc delimiter is already quoted (`<<'EOF'`), so
  the shell doesn't re-evaluate backticks and they pass through
  literally to GitHub. Writing `\`foo\`` produces literal backslash-
  backtick in the PR body, which breaks markdown code formatting. Use
  plain `` `foo` `` (and `` ```lang ``…`` ``` ``) inside the heredoc.
  If a previous PR body ended up with escaped backticks, fix with
  `gh pr edit <n> --body "$(cat <<'EOF' … EOF )"` using clean
  backticks.
- No `--no-verify`, no `--amend` after a failed hook (fix + new
  commit), no force-push to main.
- No "while we're here" refactors. Fix the bug, open the PR, stop.
- No backwards-compat shims when a clean delete is possible.
- Don't claim UI work succeeded without actually loading the page.
