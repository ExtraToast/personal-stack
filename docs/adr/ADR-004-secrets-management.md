# ADR-004: Secrets Management with HashiCorp Vault

## Status

Accepted

## Date

2026-03-25

## Context

The stack has multiple services that need access to secrets: database credentials, JWT signing keys, API keys, TLS
certificates, etc. We need centralized, auditable secrets management with rotation capabilities.

## Decision

- **Tool:** HashiCorp Vault
- **Storage Backend:** Raft integrated storage (built-in HA consensus, no external dependency)
- **Unseal Strategy:** Manual unseal with Shamir keys (pending — may switch to auto-unseal)
- **Service Auth:** AppRole (per-service role ID + secret ID)
- **UI Access:** Behind Traefik with centralized auth (vault.jorisjonkers.dev)

### Secrets Managed from Day One

- Database credentials (dynamic generation via database secrets engine)
- JWT signing keys for auth service
- TLS certificates (Vault PKI engine)
- API keys for external services (KV v2)
- Docker registry credentials (KV v2)
- Encryption keys for application data (Transit engine)
- SSH CA (Vault signs SSH keys)

### AppRole Configuration

Each service gets:

- A unique AppRole with scoped policies
- role_id baked into service config
- secret_id delivered via Docker Swarm secret or environment
- Short-lived tokens (TTL aligned with service needs)

## Consequences

- Manual unseal means downtime after server restart until operator unseals — mitigated by server stability and snapshots
- AppRole requires each service to implement Vault client login + token renewal
- Shared Kotlin library (libs/kotlin-common) should include Vault client wrapper
- Vault policies must be versioned in infra/vault/
- Raft storage needs periodic snapshots for backup
- Auto-unseal may be added later (cloud KMS or transit)
