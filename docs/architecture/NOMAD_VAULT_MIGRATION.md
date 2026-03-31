# Nomad + Vault Migration Roadmap

## Goal

Migrate `personal-stack` from Docker Swarm to Nomad + Consul + Vault while keeping
local development on the existing [docker-compose.yml](/Users/j.w.jonkers/IDEAProjects/private-stack/docker-compose.yml).

The production target is:

- `Nomad` for workload scheduling
- `Consul` for service catalog and Traefik discovery
- `Vault` for workload identity and secrets
- `Traefik` as ingress, driven by Consul Catalog tags plus a small file provider

The migration is designed around a separate Nomad host or a staged rebuild of the
production host. It is not an in-place control-plane swap inside the current Swarm stack.

## Local Development

Local development remains intentionally simple:

- use [docker-compose.yml](/Users/j.w.jonkers/IDEAProjects/private-stack/docker-compose.yml)
- do not require Nomad for day-to-day development
- do not require Vault lease renewal or secret rotation for local work

The Kotlin services now support both:

- `VAULT_AUTHENTICATION=APPROLE` for the existing Swarm/bootstrap model
- `VAULT_AUTHENTICATION=TOKEN` for Nomad-issued Vault tokens

## Production Layout

### Control Plane

The control plane runs as system services on the Nomad host:

- `consul`
- `nomad`
- `vault`

The repo ships example configs in:

- [consul-server.hcl.example](/Users/j.w.jonkers/IDEAProjects/private-stack/infra/nomad/configs/consul-server.hcl.example)
- [consul-client.hcl.example](/Users/j.w.jonkers/IDEAProjects/private-stack/infra/nomad/configs/consul-client.hcl.example)
- [nomad-server.hcl.example](/Users/j.w.jonkers/IDEAProjects/private-stack/infra/nomad/configs/nomad-server.hcl.example)
- [nomad-client.hcl.example](/Users/j.w.jonkers/IDEAProjects/private-stack/infra/nomad/configs/nomad-client.hcl.example)

### Nomad Jobs

Production workloads are split into:

- `apps`: [auth-api.nomad.hcl](/Users/j.w.jonkers/IDEAProjects/private-stack/infra/nomad/jobs/apps/auth-api.nomad.hcl), [assistant-api.nomad.hcl](/Users/j.w.jonkers/IDEAProjects/private-stack/infra/nomad/jobs/apps/assistant-api.nomad.hcl), [auth-ui.nomad.hcl](/Users/j.w.jonkers/IDEAProjects/private-stack/infra/nomad/jobs/apps/auth-ui.nomad.hcl), [assistant-ui.nomad.hcl](/Users/j.w.jonkers/IDEAProjects/private-stack/infra/nomad/jobs/apps/assistant-ui.nomad.hcl), [app-ui.nomad.hcl](/Users/j.w.jonkers/IDEAProjects/private-stack/infra/nomad/jobs/apps/app-ui.nomad.hcl)
- `data`: [postgres.nomad.hcl](/Users/j.w.jonkers/IDEAProjects/private-stack/infra/nomad/jobs/data/postgres.nomad.hcl), [valkey.nomad.hcl](/Users/j.w.jonkers/IDEAProjects/private-stack/infra/nomad/jobs/data/valkey.nomad.hcl), [rabbitmq.nomad.hcl](/Users/j.w.jonkers/IDEAProjects/private-stack/infra/nomad/jobs/data/rabbitmq.nomad.hcl)
- `platform`: [n8n.nomad.hcl](/Users/j.w.jonkers/IDEAProjects/private-stack/infra/nomad/jobs/platform/n8n.nomad.hcl), [uptime-kuma.nomad.hcl](/Users/j.w.jonkers/IDEAProjects/private-stack/infra/nomad/jobs/platform/uptime-kuma.nomad.hcl)
- `observability`: [prometheus.nomad.hcl](/Users/j.w.jonkers/IDEAProjects/private-stack/infra/nomad/jobs/observability/prometheus.nomad.hcl), [grafana.nomad.hcl](/Users/j.w.jonkers/IDEAProjects/private-stack/infra/nomad/jobs/observability/grafana.nomad.hcl), [loki.nomad.hcl](/Users/j.w.jonkers/IDEAProjects/private-stack/infra/nomad/jobs/observability/loki.nomad.hcl), [tempo.nomad.hcl](/Users/j.w.jonkers/IDEAProjects/private-stack/infra/nomad/jobs/observability/tempo.nomad.hcl), [promtail.nomad.hcl](/Users/j.w.jonkers/IDEAProjects/private-stack/infra/nomad/jobs/observability/promtail.nomad.hcl)
- `mail`: [stalwart.nomad.hcl](/Users/j.w.jonkers/IDEAProjects/private-stack/infra/nomad/jobs/mail/stalwart.nomad.hcl)
- `edge`: [traefik.nomad.hcl](/Users/j.w.jonkers/IDEAProjects/private-stack/infra/nomad/jobs/edge/traefik.nomad.hcl)

### Vault Secret Model

Dynamic or leased secrets:

- `database/creds/auth-api`
- `database/creds/assistant-api`
- `transit/keys/auth-api-jwt`

Static Vault KV used by Nomad templates:

- `secret/platform/postgres`
- `secret/platform/rabbitmq`
- `secret/platform/edge`
- `secret/platform/automation`
- `secret/platform/observability`
- `secret/platform/mail`
- `secret/auth-api`
- `secret/assistant-api`

### JWT Signing

JWT signing has moved to shared code in:

- [VaultTransitClient.kt](/Users/j.w.jonkers/IDEAProjects/private-stack/libs/kotlin-common/src/main/kotlin/com/jorisjonkers/personalstack/common/vault/VaultTransitClient.kt)

The auth service wiring stays in:

- [JwtConfig.kt](/Users/j.w.jonkers/IDEAProjects/private-stack/services/auth-api/src/main/kotlin/com/jorisjonkers/personalstack/auth/config/JwtConfig.kt)

During the cutover window, `auth-api` still publishes any legacy PEM-based key from
Vault KV alongside the new Transit-backed keyset. That avoids invalidating in-flight
tokens when Transit signing is first enabled.

## Migration Order

### Phase 1: Bootstrap the Nomad Host

1. Run [migrate-to-nomad.sh](/Users/j.w.jonkers/IDEAProjects/private-stack/infra/scripts/migrate-to-nomad.sh) `bootstrap` on the target host.
2. Install the Nomad and Consul config examples under `/etc/nomad.d` and `/etc/consul.d`.
3. Install the Vault server config under `/etc/vault.d`.
4. Initialize ACLs for Consul and Nomad.
5. Start `consul`, `nomad`, and `vault`.

### Phase 2: Prepare Vault

1. Export `VAULT_ADDR` and `VAULT_TOKEN`.
2. Run [migrate-to-nomad.sh](/Users/j.w.jonkers/IDEAProjects/private-stack/infra/scripts/migrate-to-nomad.sh) `prepare-vault`.
3. This enables `jwt-nomad`, writes the Nomad Vault policies and roles, and creates the Transit key `auth-api-jwt`.

### Phase 3: Sync Static Secrets from the Running Swarm Stack

1. Run [migrate-to-nomad.sh](/Users/j.w.jonkers/IDEAProjects/private-stack/infra/scripts/migrate-to-nomad.sh) `sync-secrets` on the current Swarm manager.
2. This extracts the live Swarm-mounted secrets from running containers and writes them into the Nomad KV paths under `secret/platform/*`.

### Phase 4: Back Up Stateful Data

1. Run [migrate-to-nomad.sh](/Users/j.w.jonkers/IDEAProjects/private-stack/infra/scripts/migrate-to-nomad.sh) `migrate`.
2. This captures:
   - a Vault raft snapshot
   - a PostgreSQL `pg_dumpall`
   - Traefik ACME state
3. Restore those assets on the target host if the migration is happening to a fresh Nomad machine.

### Phase 5: Deploy the Nomad Stack

1. Run [migrate-to-nomad.sh](/Users/j.w.jonkers/IDEAProjects/private-stack/infra/scripts/migrate-to-nomad.sh) `deploy` with `IMAGE_TAG` set to the desired image revision.
2. The script submits jobs in this order:
   - data
   - observability
   - platform
   - mail
   - replicated apps
   - edge

### Phase 6: Cut Over Traffic

1. Point DNS or the external load balancer at the Nomad host.
2. Verify the Nomad jobs are healthy.
3. Run [migrate-to-nomad.sh](/Users/j.w.jonkers/IDEAProjects/private-stack/infra/scripts/migrate-to-nomad.sh) `cutover` on the old Swarm host to scale down ingress and app traffic.
4. Run the system tests against the Nomad environment.

### Phase 7: Roll Back if Needed

If the cutover fails, run [migrate-to-nomad.sh](/Users/j.w.jonkers/IDEAProjects/private-stack/infra/scripts/migrate-to-nomad.sh) `rollback` on the old Swarm host and point DNS back to Swarm.

## Validation

Repo-side validation lives in:

- [migrate-to-nomad.sh](/Users/j.w.jonkers/IDEAProjects/private-stack/infra/scripts/migrate-to-nomad.sh) `validate`

It checks:

- `nomad fmt -check`
- `nomad job validate`
- `consul validate` for Consul configs
- `bash -n` for the Nomad scripts
- JSON syntax for Vault role/auth files

## Minimal Operator Flow

The intended operator path is:

1. On the target Nomad host, run `infra/scripts/migrate-to-nomad.sh bootstrap`.
2. On the current Swarm manager with `VAULT_ADDR` and `VAULT_TOKEN` exported, run `infra/scripts/migrate-to-nomad.sh migrate`.
3. Verify the deployed jobs with `nomad status`.
4. Switch DNS or the load balancer to the Nomad host.
5. Run `infra/scripts/migrate-to-nomad.sh cutover` on the old Swarm host.
6. If the cutover fails, run `infra/scripts/migrate-to-nomad.sh rollback` and point traffic back at Swarm.

## References

- Nomad Vault workload identity:
  https://developer.hashicorp.com/nomad/docs/secure/workload-identity/vault
- Nomad Vault ACL integration:
  https://developer.hashicorp.com/nomad/docs/integrations/vault/acl
- Nomad setup Vault reference:
  https://developer.hashicorp.com/nomad/commands/setup/vault
- Traefik Consul Catalog provider:
  https://doc.traefik.io/traefik/master/reference/routing-configuration/other-providers/consul-catalog/
