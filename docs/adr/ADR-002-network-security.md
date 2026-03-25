# ADR-002: Network Security

## Status

Accepted

## Date

2026-03-25

## Context

The server is directly exposed to the internet. We need layered security: firewall, SSH hardening, brute-force
protection, and rate limiting at the edge.

## Decision

### Firewall

- **UFW** — simple, declarative, easy to script in cloud-init
- Rules: allow 2222/tcp (SSH), 80/tcp (HTTP→HTTPS redirect), 443/tcp (HTTPS), deny all else
- Docker Swarm ports (2377, 7946, 4789) only opened when adding nodes

### SSH

- Port **2222** (non-standard to reduce noise)
- **Key-only authentication** — password auth disabled
- Root login disabled
- Configured via cloud-init

### Brute-Force Protection

- **Fail2ban** with SSH jail + Traefik jail
- SSH jail watches port 2222
- Traefik jail watches access logs for repeated 401/403

### Rate Limiting

- **Per-IP + per-authenticated-user** via Traefik rateLimit middleware
- Per-IP catches unauthenticated abuse
- Per-user prevents authenticated abuse (fair usage)

### Access Model

- Public: marketing pages (jorisjonkers.dev)
- Authenticated: all app functionality
- Admin services (Vault UI, n8n, Traefik dashboard): behind Traefik + centralized auth only (no VPN, no IP allowlist for
  now)

## Consequences

- No VPN layer means admin UIs are internet-accessible behind auth — acceptable risk for non-critical personal stack
- WireGuard VPN may be added later for admin services
- IP allowlisting may be added later for Vault/n8n/Traefik dashboard
- Per-user rate limiting requires auth integration in Traefik middleware
- Fail2ban needs log access from Traefik container
