# Stalwart v0.15.5 → v0.16 migration plan

Status: dev (Phase A) implemented and validated against a real
`stalwartlabs/stalwart:v0.16.6` instance. Production (Phases B–D) is
documented here and gated on an explicit maintenance window — the
first boot of v0.16 **wipes** the directory + settings subspaces, so it
is destructive and irreversible without a backup.

All object shapes, the apply-plan NDJSON format, the bootstrap flow,
idempotency behaviour, the Cloudflare DNS-01 wiring, and the webui
download dependency below were verified end-to-end with `stalwart-cli`
v1.0.7 against v0.16.6, not inferred from docs.

## Why this change

v0.16 removed TOML configuration entirely. There is now a single
`config.json` on disk describing only the datastore; every other
setting (hostname, listeners, ACME, domains, accounts, tracers) is a
JMAP object stored in the database and reconciled with
`stalwart-cli apply`. This is the same redesign that, on 2026-04-20,
broke our pinned-to-`:latest` assumption and forced the v0.15.5 pin in
`docker-compose.yml`. It also removes the entire class of bug that PR
#440 worked around: macros (`%{env:...}%`) only ever expanded for
"local" TOML keys, and there are no local keys anymore — `apply`
writes fully-resolved values into the database.

Upstream reference: <https://github.com/stalwartlabs/stalwart/blob/main/UPGRADING/v0_16.md>,
<https://stalw.art/docs/configuration/declarative-deployments/>,
<https://stalw.art/docs/management/cli/apply/>.

## Key facts established against the live instance

- **config.json** is a bare datastore object: `{"@type":"RocksDb","path":"/var/lib/stalwart"}`.
- **Docker mount points changed**: old image used `/opt/stalwart`; v0.16
  uses `/etc/stalwart` (config) + `/var/lib/stalwart` (data), and runs
  as UID 2000.
- **Apply plan is NDJSON**, one op per line, no enclosing array. Each op
  is `{"@type":"create|update|destroy","object":"<Type>","value":...}`.
  Singletons (`SystemSettings`, `DataStore`, …) use `update` with the
  fields directly in `value`; collections (`Domain`, `NetworkListener`,
  …) use `create` with `value` keyed by a create-id. Cross-references
  use `#<create-id>` and resolve within a single apply.
- **`create` is not idempotent** — re-applying a plan that creates an
  existing object raises `primaryKeyViolation`. The apply step must be
  guarded (we guard on "does the deployment domain exist yet").
- **Durations are integers in milliseconds** (e.g. `60000`, not `"60s"`).
- **The webui bundle is downloaded from github.com** (`stalwartlabs/webui`
  releases) on first boot and unpacked to `/tmp`. It is ephemeral and
  re-downloaded each boot, so the container needs outbound HTTPS to
  github. A first-boot network race yields `/admin/ → 404`; a restart
  fixes it. The rendered login page is titled **"Portal"** and shows an
  account-name form — it does _not_ contain the string "stalwart".
- **Plaintext submission (587) and IMAP (143) listeners are not created
  by default** in v0.16 (PACC compliance). The default set is smtp:25,
  submissions:465, imaps:993, pop3s:995, sieve:4190, http:8080,
  https:443. We re-add the plaintext listeners we depend on via the
  plan.
- **REST `/api/...` is gone**, replaced by JMAP at `/jmap`. The existing
  `principal-bootstrap.sh` (which POSTs `/api/principal/auth`) will stop
  working and must be rewritten as an `apply` that creates an Account.
- **Cloudflare DNS-01**: a `DnsServer` object of variant `Cloudflare`
  with `secret: {"@type":"Value","secret":"<token>"}`, plus an
  `AcmeProvider` with `challengeType: "Dns01"`, plus the `Domain`
  pointing at both via `certificateManagement:
{"@type":"Automatic","acmeProviderId":"#acme-le"}` and
  `dnsManagement: {"@type":"Automatic","dnsServerId":"#dns-cf"}`. Both
  automatic blocks must be set together — setting cert management alone
  fails with "ACME provider requires automatic DNS management".

## Artifacts in this repo

- `infra/stalwart/config.json` — datastore-only config, mounted at
  `/etc/stalwart/config.json`.
- `infra/stalwart/plan.dev.ndjson` — dev apply plan (Domain
  `jorisjonkers.test`, hostname `stalwart.jorisjonkers.test`, plaintext
  submission+imap listeners, stdout tracer). No ACME (dev is fronted by
  Traefik TLS).
- `infra/stalwart/plan.prod.ndjson.tmpl` — prod plan template with
  `${STALWART_HOSTNAME}`, `${STALWART_DOMAIN}`, `${CF_DNS_API_TOKEN}`
  placeholders; rendered with `envsubst` at apply time so secrets never
  live in git or in a snapshot. Includes the Cloudflare/ACME wiring.
- `infra/stalwart/bootstrap.sh` + `Dockerfile.bootstrap` — ships the
  pinned `stalwart-cli`, waits for the webadmin, guards on the domain,
  and runs `apply`. Used by the `stalwart-bootstrap` compose service and
  reused as the basis for the production apply Job.

## Phase A — dev (DONE)

`docker-compose.yml` now runs `stalwartlabs/stalwart:v0.16.6` with the
new mounts + `config.json` and a `STALWART_RECOVERY_ADMIN` credential,
and a `stalwart-bootstrap` init service applies `plan.dev.ndjson` once
the webadmin is healthy. `MainSiteServiceLaunchTest`'s assertion changed
from `"stalwart management"` (gone in v0.16) to the webadmin document
title `"portal"`, validated by rendering the real webadmin headlessly.

Validation performed: clean-boot apply (4 ops), idempotent re-run
(guarded skip), bootstrap image built and run twice against a real
container, prod-style plan with `#`-references applied cleanly.

## Phase B — production manifests (NOT YET APPLIED)

New/changed files under `platform/cluster/flux/apps/mail/stalwart/`:

1. **`config-json-configmap.yaml`** — replaces `config-template-configmap.yaml`.
   Holds `config.json` (rocksdb at `/var/lib/stalwart`).
2. **`plan-template-configmap.yaml`** — holds `plan.prod.ndjson.tmpl`.
   Content-hashed via `configMapGenerator` so a plan change forces the
   apply Job to re-run.
3. **`deployment.yaml`** — image → `stalwartlabs/stalwart:0.16.x`
   (pinned, Keel manages point bumps under `match-tag: "true"`); mounts
   → `/etc/stalwart/config.json` + `stalwart-data:/var/lib/stalwart`;
   drop the `cp`/`set -a . env` entrypoint shim (v0.16 reads `config.json`
   - env directly); keep the Vault Agent injector. Note the hostPort
     list must add 587/143/110 only if the plan creates those listeners.
     Outbound HTTPS to github must be allowed for the webui download.
4. **`apply-plan-job.yaml`** — Job (one per plan-template hash, with
   `kustomize.toolkit.fluxcd.io/force: enabled`). Init container runs
   `envsubst < plan.prod.ndjson.tmpl > /shared/plan.ndjson` using the
   Vault-injected `STALWART_HOSTNAME`, `STALWART_DOMAIN`,
   `CF_DNS_API_TOKEN`; main container runs `stalwart-cli apply` against
   `http://stalwart.mail-system.svc.cluster.local:8080`. Built from
   `Dockerfile.bootstrap`.
5. **Rewrite `principal-bootstrap.sh`** — the v0.15 REST call
   (`POST /api/principal/auth`) is dead. Replace with a
   `stalwart-cli apply` that creates `Account` `auth@jorisjonkers.dev`
   (User variant, password credential from Vault) so auth-api can submit
   over SMTP AUTH on 587. Account names are now full email addresses.
6. **`PlatformMailFluxTest`** — update assertions: no more
   `config.toml`/`acme."letsencrypt"` strings; assert on the new
   ConfigMaps, the apply Job, and the v0.16 image tag.

## Phase C — data migration + cutover (destructive; scheduled window)

The storage layer (mail, calendars, contacts) is untouched. The wipe
hits the directory + settings subspaces only. Sequence:

1. **Pre-flight (no downtime)**: on the workstation, run
   `migrate_v016.py dump` against the live v0.15.5 admin API to produce
   `settings.json` + `principals.json`, then `convert` with
   `--patch-paths /opt/stalwart=/var/lib/stalwart` to emit `config.json`
   - `export.json`. Review both. The script migrates accounts, domains,
     stores, DKIM, and issued certificates — **not** listeners, ACME,
     routing, spam, or logging (those come from `plan.prod.ndjson.tmpl`).
     Script: <https://raw.githubusercontent.com/stalwartlabs/stalwart/main/resources/scripts/migrate_v016.py>.
2. **Backup**: `kubectl scale deploy/stalwart -n mail-system --replicas=0`,
   then tar `/var/lib/stalwart` (currently `/opt/stalwart/data`) off the
   PVC to a safe location. This is the only path back to v0.15.
3. **Recovery-mode apply**: bring up a throwaway v0.16 pod/container with
   `STALWART_RECOVERY_MODE=1` + `STALWART_RECOVERY_ADMIN`, pointed at the
   migrated data. It wipes the incompatible subspaces and exposes 8080.
   `stalwart-cli apply --file export.json`, then
   `stalwart-cli apply --file <rendered plan.prod.ndjson>` for the
   settings the script does not carry (listeners, ACME, hostname).
4. **Normal start**: tear down the recovery pod; apply Phase B manifests
   so Flux runs the real Deployment (no `STALWART_RECOVERY_MODE`) + the
   apply Job. Verify: webadmin Network→Hostname shows
   `mail.jorisjonkers.dev` (not a macro literal), ACME DNS-01 issues a
   cert via Cloudflare, SMTP/IMAP reachable, auth-api can submit mail.
5. **Post-migration**: trigger "Recalculate disk quotas" from the
   webadmin Tasks panel (quotas are reset to zero by the wipe).

**Rollback**: scale to 0, restore the `/var/lib/stalwart` tar, redeploy
the v0.15.5 image + the old config-template ConfigMap. The v0.16 binary
refuses to start twice against an already-migrated store, so the backup
is the only way back.

## Phase D — cleanup (after a week stable on v0.16)

- Delete `config-template-configmap.yaml` and remove the
  `config.local-keys` workaround from PR #440 (no TOML exists anymore).
- Update `CLAUDE.md` mail notes to the v0.16 declarative model.

## Open items to confirm during the prod window

- Whether v0.16 auto-migrates the existing rocksdb store on first
  recovery-mode boot, or whether `export.json` replay is the only path
  for the directory records (the script + recovery mode are designed for
  the latter; confirm no double-migration).
- The exact hostPort set to publish — only add 587/143/110 if the prod
  plan keeps those listeners and mail clients still need plaintext+STARTTLS.
- Whether to stage the webui bundle internally rather than depend on a
  github fetch at every boot on the Frankfurt VPS.
