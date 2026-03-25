# ADR-003: TLS & DNS Strategy

## Status

Accepted

## Date

2026-03-25

## Context

All services use subdomains of jorisjonkers.dev. We need automated TLS certificate provisioning and renewal. The domain
is registered and DNS is managed via Cloudflare.

## Decision

- **DNS Provider:** Cloudflare (free tier)
- **DNS Mode:** DNS-only (grey cloud) — Traefik terminates TLS directly, no Cloudflare proxy
- **Certificate Strategy:** Traefik built-in ACME with DNS-01 challenge
- **Wildcard cert:** `*.jorisjonkers.dev` + `jorisjonkers.dev`
- **Challenge:** DNS-01 via Cloudflare API token (scoped to Zone:DNS:Edit for jorisjonkers.dev)

### Cloudflare Setup

The domain jorisjonkers.dev is registered elsewhere. To use Cloudflare DNS:

1. Create Cloudflare account, add jorisjonkers.dev as a site
2. Cloudflare provides two nameservers
3. Update nameservers at the domain registrar to point to Cloudflare's nameservers
4. Create DNS records in Cloudflare (A record pointing to Contabo VPS IP)
5. Create scoped API token for Traefik ACME: Zone:DNS:Edit permission for jorisjonkers.dev zone only

### Traefik ACME Config

- Certificate resolver uses Cloudflare DNS provider
- ACME email for Let's Encrypt notifications
- Certificate stored in Traefik's acme.json (persisted via Docker volume)

## Consequences

- Single wildcard cert covers all subdomains — no cert management per service
- DNS-01 doesn't require port 80 open for challenges (though we keep 80 for HTTP→HTTPS redirect)
- Cloudflare free tier has no SLA — acceptable for personal stack
- API token must be stored securely (Vault or Docker secret)
- Renewal is fully automatic via Traefik
- Adding new subdomains requires only a DNS A record — cert already covers wildcards
