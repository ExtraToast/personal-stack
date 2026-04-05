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
- Run `infra/scripts/setup.sh prepare-pure-vault` once on the server when switching to the env-driven Vault bootstrap flow. This is the one-time prep step, not a recurring CI job.
- After that, use `infra/scripts/deploy.sh` for normal deploys. Pass `NOMAD_TOKEN` plus the image/domain inputs from CI or your shell; steady-state deploys should not depend on `.nomad-bootstrap.env`, `.vault-keys`, or `.nomad-keys`.
- Use Vault workload identity and per-job JWT roles instead of AppRole.
- Keep only the bootstrap/control-plane secret set in Vault KV; app runtime secrets should come from Vault secret engines.

See [NOMAD_VAULT_MIGRATION.md](/Users/j.w.jonkers/IDEAProjects/private-stack/docs/architecture/NOMAD_VAULT_MIGRATION.md) for the implementation roadmap.
