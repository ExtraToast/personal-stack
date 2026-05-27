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

## Phase B — production manifests (IMPLEMENTED, NOT YET CUT OVER)

Live files under `platform/cluster/flux/apps/mail/stalwart/`:

1. **`config-json-configmap.yaml`** (`stalwart-config-json`) — datastore
   only: `{"@type":"RocksDb","path":"/var/lib/stalwart/data"}`. The path
   matches the rocksdb the v0.15 PVC already holds under `data/` once the
   PVC is remounted at `/var/lib/stalwart`, and matches the
   `--patch-paths /opt/stalwart=/var/lib/stalwart` output of the migration
   script. The existing PVC is reused; no data copy.
2. **`plan.ndjson.tmpl`** — the settings the migration does NOT carry:
   the Cloudflare `DnsServer` (token `${CF_DNS_API_TOKEN}`), the Let's
   Encrypt `AcmeProvider` (DNS-01), and the plaintext submission/IMAP/POP3
   listeners v0.16 drops by default. Validated against
   `infra/stalwart/schema.json` by `infra/stalwart/validate-plan.py`.
3. **`apply.sh`** + **`kustomization.yaml` `configMapGenerator`
   (`stalwart-apply`)** — content-hashed so a script/plan edit rolls the
   Deployment.
4. **`deployment.yaml`** — `stalwartlabs/stalwart:v0.16.6`, mounts
   `/etc/stalwart/config.json` + `stalwart-data:/var/lib/stalwart`, the
   v0.15 entrypoint shim removed. A **`stalwart-apply` sidecar** (alpine +
   downloaded `stalwart-cli`) waits for the webadmin then reconciles on
   every boot: applies the settings plan when absent, wires the migrated
   domain to automatic ACME + Cloudflare DNS, sets the hostname, **renews
   the CF DNS-01 token** in the `DnsServer` object, and ensures the
   `auth@jorisjonkers.dev` SMTP account. Idempotent (validated live).
5. **`vault-static-secrets.yaml`** — three `VaultStaticSecret`s
   (`stalwart-edge` → CF token, `stalwart-mail` → admin + composite
   `STALWART_RECOVERY_ADMIN`, `stalwart-auth-mail` → auth-api SMTP creds),
   each with `rolloutRestartTargets` → the `stalwart` Deployment. **This
   is the "restart on secret change" mechanism**: VSO re-syncs on
   rotation, the k8s Secret changes, VSO rolls the Deployment, and the
   sidecar re-applies the fresh value on the next boot. Requires the
   `vault-secrets-operator` SA in `mail-system` (created here) plus the
   `bootstrap-auth.sh` edits: `secret/data/platform/mail` +
   `secret/data/auth-api` added to the `vso` policy, `mail-system` added
   to the `vso` role's bound namespaces.
6. **`principal-bootstrap-job.yaml` + `principal-bootstrap.sh` removed** —
   the v0.15 REST call (`POST /api/principal/auth`) is gone; the auth
   account is now provisioned by the apply sidecar. `config-template-configmap.yaml`
   removed (no TOML).
7. **`PlatformMailFluxTest`** updated to the new shape (all green).

The committed manifests are the _steady state_. They do not perform the
one-time data migration in Phase C — applying them against the current
v0.15 PVC would have v0.16 wipe-then-bootstrap a fresh datastore. **Do
not let Flux reconcile these to prod until Phase C has run in a window.**
Keep `apps-mail` suspended (`flux suspend kustomization apps-mail`) while
this PR merges, until the cutover.

## Phase C — one-time data-preserving cutover (scheduled window)

Mail/calendar/contact data is preserved: the v0.16 first boot deletes only
the directory + settings _records_, and `export.json` recreates each
account with its original id (`restore-<id>`) so it re-links to the
on-disk mail. Sequence:

1. **`flux suspend kustomization apps-mail`** so Flux does not race the
   manual steps.
2. **Pre-flight (no downtime)**: from the workstation, port-forward the
   live v0.15 admin API and run the migration script:
   ```
   python migrate_v016.py dump --url http://127.0.0.1:8080 \
     --username "$ADMIN" --password "$PW" \
     --settings settings.json --principals principals.json
   python migrate_v016.py convert \
     --settings settings.json --principals principals.json \
     --config config.json --output export.json \
     --patch-paths /opt/stalwart=/var/lib/stalwart
   ```
   Review `config.json` (should match `config-json-configmap.yaml`) and
   `export.json`. Script:
   <https://raw.githubusercontent.com/stalwartlabs/stalwart/main/resources/scripts/migrate_v016.py>.
3. **Backup**: `kubectl scale deploy/stalwart -n mail-system --replicas=0`,
   then tar the PVC contents off-node. Only path back to v0.15.
4. **Recovery-mode apply**: run a throwaway v0.16.6 pod with
   `STALWART_RECOVERY_MODE=1` + `STALWART_RECOVERY_ADMIN`, the existing PVC
   mounted at `/var/lib/stalwart`, and the migrated `config.json`. It
   wipes the incompatible subspaces and exposes 8080. Then, with
   `stalwart-cli` v1.0.7+:
   ```
   stalwart-cli apply --file export.json     # accounts/domains/stores
   ```
   The listeners/ACME/hostname/auth-account come from the steady-state
   sidecar, so no separate settings apply is needed here.
5. **Normal start**: delete the recovery pod, then
   `flux resume kustomization apps-mail`. Flux applies Phase B; the
   Deployment starts normally and the `stalwart-apply` sidecar reconciles
   settings + renews the CF token. Verify: webadmin Network→Hostname shows
   `mail.jorisjonkers.dev` (no macro literal), ACME DNS-01 issues a cert
   via Cloudflare, SMTP/IMAP reachable, auth-api submits mail.
6. **Post-migration**: trigger "Recalculate disk quotas" in the webadmin
   Tasks panel (quotas reset to zero by the wipe).

**Rollback**: scale to 0, restore the PVC tar, revert this PR (v0.15.5
image + the old config-template ConfigMap). The v0.16 binary refuses to
start twice against an already-migrated store, so the backup is the only
way back.

## Phase D — cleanup (after a week stable on v0.16)

- Retire the now-unused `stalwart` and `stalwart-bootstrap` Vault roles +
  policies in `bootstrap-auth.sh` (the deployment uses VSO now, not the
  Vault Agent). Left in place during cutover to avoid touching auth the
  rollback might need.
- Remove the `config.local-keys` workaround from PR #440 (no TOML exists).
- Update `CLAUDE.md` mail notes to the v0.16 declarative model.

## Open items to confirm during the prod window

- Confirm the recovery-mode boot + `export.json` replay is the path for
  directory records (no auto-migration double-run).
- Consider staging the webui bundle internally rather than fetching from
  github on every boot on the Frankfurt VPS; the `stalwart-apply` sidecar
  likewise downloads `stalwart-cli` from github each boot — a baked
  toolbox image pushed to ghcr would remove both runtime fetches.
