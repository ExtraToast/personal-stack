# ADR-005: Authentication & Authorization

## Status

Accepted

## Date

2026-03-25

## Context

All services in the stack need centralized authentication. We need a solution that gives us full control, fits the
Kotlin/Spring ecosystem, and supports OAuth2/OIDC for standards compliance.

## Decision

### Auth Service

- **Custom Kotlin auth service** using Spring Authorization Server
- Full control over auth flows, lightweight, no external dependency
- Chosen over Keycloak (too heavy, ~500MB RAM, complex config, upgrade pain)

### Protocol

- **OAuth2/OIDC with JWT access tokens**
- Standards-compliant, stateless verification
- Frontend can decode tokens for display (roles, username)

### Token Strategy

- Access token: **15 minutes** (short exposure window)
- Refresh token: **7 days** (reasonable UX before re-login)
- Tokens signed with keys stored in Vault

### Auth Enforcement

- **Traefik forward-auth middleware** — every request hits the auth service
- Services receive verified user headers (X-User-Id, X-User-Roles, etc.)
- Services don't need JWT validation logic — just read headers
- Public routes (marketing pages) bypass forward-auth via Traefik rule

### Multi-Factor Authentication

- **TOTP from day one** (Google Authenticator / Authy compatible)
- Required for admin accounts, optional for regular users initially

### Role Model

- **Simple RBAC: admin / user / readonly**
- Roles stored in JWT claims
- Architecture designed for future evolution to fine-grained RBAC:
  - Permission model abstracted behind interfaces
  - Role-to-permission mapping in database (not hardcoded)
  - @PreAuthorize uses hasPermission() evaluators, not hasAuthority()

### Security on Every Endpoint

- All controller endpoints must have @PreAuthorize or @PermitAll annotation
- @PreAuthorize must use hasPermission(...) — standalone hasAuthority() is forbidden
- Permission evaluators live in the infrastructure layer
- Enforced by ArchUnit tests

### Route Authorization (Frontend)

- Route meta is the single source of truth for UI access: requiresAuth, requiredRoles, featureFlag
- One global navigation guard evaluates all access
- Frontend guards are UX-only — the backend (via forward-auth) remains the authorization authority

### Service-to-Service Auth

- **Vault-issued short-lived tokens / mTLS**
- Dynamic, rotatable, with audit trail via Vault
- Each service pair has scoped Vault policies

## Consequences

- Auth service is a critical dependency — if it's down, forward-auth blocks all requests
- Valkey used for auth session/refresh token storage (fast revocation)
- Must implement TOTP enrollment flow in auth-ui
- Forward-auth adds latency to every request — mitigated by Valkey caching in auth service
- RBAC migration path requires permission model to be database-driven from the start
- mTLS between services requires Vault PKI engine + certificate rotation
