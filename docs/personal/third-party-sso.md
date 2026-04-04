# Third-Party SSO

The auth service is the identity provider for the stack. Third-party services do not share the auth
service's `SESSION` cookie directly. Instead, they authenticate as OAuth/OIDC clients against the
auth service. Because the browser is already logged in on `auth.jorisjonkers.*`, those clients can
reuse the existing auth session and complete login without a separate password prompt.

## Supported patterns

- Grafana: native OIDC client. Exposed directly through Traefik and configured in `docker-compose.yml`
  and `docker-compose.prod.yml`.
- Vault: native OIDC client. Production bootstrap is configured in `infra/vault/scripts/init-vault.sh`,
  and existing instances are repaired through `infra/repair-server.sh`.
- RabbitMQ management: native OAuth/OIDC resource server and management UI integration, exposed directly
  instead of being pre-gated by forward-auth.
- Stalwart: uses internal directory for account management (no OIDC integration).
  Admin access to the webadmin uses the fallback-admin credentials (see `infra/stalwart/config.toml`).
- n8n: configured through the custom external hook in `infra/n8n/hooks.js` so it can delegate login to
  the auth service without a separate Traefik auth gate.
- Uptime Kuma: intentionally left outside this SSO work.

## Practical model

1. A user logs into `auth.jorisjonkers.dev` or `auth.jorisjonkers.test`.
2. Grafana, Vault, RabbitMQ, or n8n redirects the browser to the auth service's authorization endpoint.
3. The auth service sees the existing browser session and immediately issues an authorization code.
4. The downstream service exchanges that code for tokens and creates its own local session.

That gives you centralized authentication without maintaining separate usernames and passwords inside
each service.
