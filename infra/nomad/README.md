# Nomad Deployment Layout

This directory contains the production Nomad deployment assets for migrating
`personal-stack` from Docker Swarm to Nomad + Consul + Vault workload identity.

Local development stays on the existing [docker-compose.yml](/Users/j.w.jonkers/IDEAProjects/private-stack/docker-compose.yml).
Nomad is the production deployment target only.

## Layout

```text
infra/nomad/
  configs/        Nomad and Consul agent configuration examples
  jobs/
    apps/         Replicated app and UI jobs
    data/         Stateful data services
    edge/         Traefik ingress
    mail/         Stalwart mail server
    observability/ Prometheus/Grafana/Loki/Promtail/Tempo
    platform/     Single-instance platform services such as n8n and uptime-kuma
  templates/      Nomad template fragments rendered into task env/files
  vault/          Vault JWT auth config, policies, and per-job roles
```

## Usage

- Treat these files as the canonical production deployment definitions.
- Keep Nomad, Consul, and Vault themselves as system services on the host.
- Use [migrate-to-nomad.sh](/Users/j.w.jonkers/IDEAProjects/private-stack/infra/scripts/migrate-to-nomad.sh) as the single operational entrypoint.
- For a full staged migration, run `bootstrap`, `sync-secrets`, `prepare-vault`, `deploy`, `cutover`, and `rollback` through that script.
- Use Vault workload identity and per-job JWT roles instead of AppRole.
- Keep only a small static secret set in Vault KV; local Compose does not need that.

See [NOMAD_VAULT_MIGRATION.md](/Users/j.w.jonkers/IDEAProjects/private-stack/docs/architecture/NOMAD_VAULT_MIGRATION.md) for the implementation roadmap.
