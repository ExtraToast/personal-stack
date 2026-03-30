# Third-Party SSO

The auth service is the identity provider for the stack. Third-party services do not share the auth
service's `SESSION` cookie directly. Instead, they authenticate as OAuth/OIDC clients against the
auth service. Because the browser is already logged in on `auth.jorisjonkers.*`, those clients can
reuse the existing auth session and complete login without a separate password prompt.

## Supported patterns

- Grafana: native OIDC client. Configured in `docker-compose.yml` and `docker-compose.prod.yml`.
- Vault: native OIDC client. Production bootstrap is configured in `infra/vault/scripts/init-vault.sh`,
  and existing instances are repaired through `infra/repair-server.sh`.
- RabbitMQ management: native OAuth/OIDC resource server and management UI integration.
- Stalwart: still protected by forward-auth here. It is not using the same browser-redirect OIDC client
  flow as Grafana, Vault, RabbitMQ, and n8n in this stack.
- n8n: configured through the custom external hook in `infra/n8n/hooks.js` so it can delegate login to
  the auth service.
- Uptime Kuma: intentionally left outside this SSO work.

## Practical model

1. A user logs into `auth.jorisjonkers.dev` or `auth.jorisjonkers.test`.
2. Grafana or Vault redirects the browser to the auth service's authorization endpoint.
3. The auth service sees the existing browser session and immediately issues an authorization code.
4. The downstream service exchanges that code for tokens and creates its own local session.

That gives you centralized authentication without maintaining separate usernames and passwords inside
each service.
