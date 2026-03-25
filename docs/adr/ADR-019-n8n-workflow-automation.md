# ADR-019: n8n Workflow Automation

## Status

Accepted

## Date

2026-03-25

## Context

The stack needs workflow automation for deployment, monitoring, scheduled tasks, and external integrations. n8n provides
a self-hosted, visual workflow builder with a large connector ecosystem.

## Decision

### Deployment

- n8n runs as a Docker Swarm service
- Accessible at n8n.jorisjonkers.dev
- Behind Traefik with centralized auth (forward-auth middleware)

### Database

- Separate database (`n8n_db`) in the shared PostgreSQL instance
- Vault provisions n8n's database credentials

### Use Cases

1. **Deployment automation:** trigger deploys, run post-deploy checks, notify on completion
2. **Alert routing:** receive Grafana/Uptime Kuma webhooks, enrich context, route to Discord/Email
3. **Scheduled tasks:** database backups, log cleanup, certificate monitoring reports
4. **Data sync:** synchronize data between services on schedule
5. **External API integrations:** connect to third-party services without building custom code
6. **Custom workflows:** any automation that doesn't warrant a dedicated service

### Security

- n8n has powerful integrations — restrict access to admin users only
- API credentials stored in Vault, injected via environment or n8n credential store
- Webhook endpoints exposed via Traefik but rate-limited

## Consequences

- n8n adds ~300MB RAM to the stack
- Behind centralized auth means n8n's built-in auth is secondary — but still configured as a safety net
- Workflow definitions should be version-controlled (n8n supports export/import)
- n8n upgrades must be tested carefully — workflow compatibility can break between versions
