# Stalwart Mail Server — Post-Deploy Setup

After deploying the stack, Stalwart needs one-time manual setup for domain, account,
and email routing configuration. Some steps use the webadmin UI, others use the
auth-api (identity provider) since Stalwart's primary directory is OIDC-backed.

## Prerequisites

- Stack is running (`docker compose up -d` or `docker stack deploy`)
- You are signed into `auth.jorisjonkers.dev` (forward-auth gates the webadmin)

## 1. Create your account in the identity provider

Stalwart uses the `auth-api` OIDC directory as its primary directory, so user accounts
are managed through the auth service — not through Stalwart's webadmin.

1. Navigate to `https://auth.jorisjonkers.dev/register` (prod) or
   `https://auth.jorisjonkers.test/register` (dev)
2. Register with your desired username, email, and password
3. Confirm your email address via the confirmation link
4. (Optional) Set up TOTP 2FA at `https://auth.jorisjonkers.dev/totp-setup`

To grant yourself admin access and mail permissions, connect to the auth database:

```bash
# On the server, exec into the postgres container
docker exec -it $(docker ps -q -f name=postgres) psql -U auth_user -d auth_db

# Grant admin role
UPDATE app_user SET role = 'ADMIN' WHERE username = 'YourUsername';

# Grant mail permission
INSERT INTO user_service_permissions (user_id, service)
SELECT id, 'MAIL' FROM app_user WHERE username = 'YourUsername';
```

Once your account exists and has the MAIL permission, Stalwart will recognize you
when you authenticate with an OAuth2 bearer token via the `/api/userinfo` endpoint.

## 2. Log into the Stalwart webadmin

Navigate to `https://stalwart.jorisjonkers.dev` (prod) or
`https://stalwart.jorisjonkers.test` (dev).

Use the fallback admin credentials (these are separate from your auth-api account):

- **Dev**: `admin` / `stalwart-dev-admin`
- **Prod**: `admin` / the password from the `stalwart_admin_password` Docker Swarm secret

To read the prod admin password on the server:

```bash
docker exec $(docker ps -q -f name=stalwart) cat /run/secrets/stalwart_admin_password
```

> **Note**: The hostname (`server.hostname`) is already configured via the
> `STALWART_HOSTNAME` environment variable (`mail.jorisjonkers.dev` in prod,
> `stalwart.jorisjonkers.test` in dev). Do not change it in the webadmin — doing so
> can trigger ACME reload errors.

## 3. Create the mail domain

1. Go to **Directory > Domains**
2. Click **+ Create domain**
3. Enter `jorisjonkers.dev` (prod) or `jorisjonkers.test` (dev)
4. Save

## 4. Configure catch-all

A catch-all ensures all mail sent to any address `@jorisjonkers.dev` is delivered to
your account, even if the specific address doesn't exist.

Since the OIDC directory is read-only, configure catch-all at the server level:

1. Go to **Settings > SMTP > Inbound > RCPT stage**
2. Set **Catch-all** to `true`

With catch-all enabled, any email sent to `anything@jorisjonkers.dev` will be delivered
to the account that has `@jorisjonkers.dev` as an associated address.

## 5. ACME / TLS configuration

TLS certificates for the mail listeners (IMAP, SMTP, etc.) are managed by Stalwart
via ACME with Let's Encrypt and Cloudflare DNS-01 challenge. This is already configured
in `config.toml`, but the Cloudflare API token must be a real token (not a placeholder).

To verify/update the Cloudflare DNS API token:

```bash
# Check current value
docker exec $(docker ps -q -f name=stalwart) cat /run/secrets/cf_dns_api_token

# If it's "placeholder", create a real token:
# 1. Go to https://dash.cloudflare.com/profile/api-tokens
# 2. Create a token with "Edit zone DNS" permission for jorisjonkers.dev
# 3. Update the Docker secret:
docker secret rm cf_dns_api_token
printf '%s' 'your-real-token' | docker secret create cf_dns_api_token -
# 4. Redeploy the stack to pick up the new secret
```

The webadmin hostname is served behind Traefik (which handles its own TLS), so ACME
only applies to `mail.jorisjonkers.dev` for direct mail protocol connections.

## 6. DKIM signing (recommended)

DKIM cryptographically signs outgoing email to improve deliverability.

1. Go to **Directory > Domains**, edit `jorisjonkers.dev`
2. Click **Generate DKIM key** (or Stalwart may auto-generate one)
3. Copy the displayed DNS TXT record
4. Add it to Cloudflare DNS as a TXT record for the selector shown
   (e.g. `default._domainkey.jorisjonkers.dev`)

## 7. DNS records

These records must be added in Cloudflare (or your DNS provider):

| Type | Name                                  | Value                                                       |
| ---- | ------------------------------------- | ----------------------------------------------------------- |
| MX   | `jorisjonkers.dev`                    | `mail.jorisjonkers.dev` (priority 10)                       |
| A    | `mail.jorisjonkers.dev`               | `<server IP>`                                               |
| TXT  | `jorisjonkers.dev`                    | `v=spf1 mx -all`                                            |
| TXT  | `default._domainkey.jorisjonkers.dev` | (from DKIM setup above)                                     |
| TXT  | `_dmarc.jorisjonkers.dev`             | `v=DMARC1; p=quarantine; rua=mailto:admin@jorisjonkers.dev` |

The A record for `mail.jorisjonkers.dev` should already exist if the server was
provisioned with the standard DNS setup.

## 8. Test

- **Send inbound**: Send an email from an external address to `test@jorisjonkers.dev`.
  It should arrive in your mailbox (catch-all).
- **Send outbound**: Configure a mail client (Thunderbird, etc.) with:
  - IMAP: `mail.jorisjonkers.dev:993` (TLS)
  - SMTP: `mail.jorisjonkers.dev:587` (STARTTLS) or `:465` (TLS)
  - Username: your auth-api username
  - Auth: OAUTHBEARER (preferred) or password
- **Check DKIM**: Send an email to `check-auth@verifier.port25.com` and review the report.
