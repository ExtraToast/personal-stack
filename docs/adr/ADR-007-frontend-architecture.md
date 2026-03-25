# ADR-007: Frontend Architecture — Feature-Based with Domain Boundaries

## Status

Accepted

## Date

2026-03-25

## Context

Multiple Vue frontends (auth-ui, assistant-ui, app-ui) share a common library. We need a consistent architecture that
scales, enforces boundaries, and prevents the common pitfall of scattered related code.

## Decision

### Architectural Style

**Feature-based structure** for all Vue applications.

### Directory Structure

```
src/
├── features/              # Business domains
│   ├── auth/
│   │   ├── components/    # Feature-specific components
│   │   ├── composables/   # Feature-specific composables
│   │   ├── services/      # API calls for this feature
│   │   ├── stores/        # Pinia stores for this feature
│   │   ├── types/         # TypeScript types for this feature
│   │   └── views/         # Route-level components
│   └── dashboard/
│       └── ...
├── shared/                # Cross-cutting concerns
│   ├── components/        # Base/common components (domain-agnostic)
│   ├── composables/       # Shared composables (useAuth, useApi, etc.)
│   ├── services/          # HTTP client, API base, error handling
│   └── types/             # Shared TypeScript types
├── router/                # Vue Router config
├── stores/                # Global-only stores (app config, auth state)
└── App.vue
```

### Domain Boundaries

- Each feature is a self-contained domain module
- Cross-feature access goes through the feature's barrel export (index.ts)
- Never deep-import another feature's internals
- Shared components must remain domain-agnostic

### Generated API Clients

- Auto-generated from OpenAPI spec (backend is source of truth)
- Generated code lives in shared/services/api/generated/ — **never manually edited**
- Excluded from ESLint and coverage
- Domain adapters in each feature's services/ wrap generated clients
- Components consume domain models, never generated response types

### Composition API Only

- All components use `<script setup lang="ts">`
- No Options API
- Templates stay declarative — complex logic goes into composables
- Feature composables live inside the owning feature
- Enforced by ESLint

### State Management

- Pinia for all state management (no Vuex)
- Feature stores live in the feature directory
- Global stores (auth, app config) live in src/stores/
- Store actions must not directly call API — go through service layer

### Route Authorization

- Route meta is the single source of truth: requiresAuth, requiredRoles, featureFlag
- One global navigation guard evaluates all access decisions
- Frontend guards are UX-only — backend forward-auth is the real authority

### Form Validation (Three Stages)

1. **UI interaction:** Validation rules on form fields (real-time feedback)
2. **Schema validation:** Zod for command payload shape validation before submission
3. **Backend error reconciliation:** RFC 7807 Problem Details → field-level errors

## Consequences

- Feature isolation prevents spaghetti imports as apps grow
- Generated API client pattern means backend changes auto-propagate to frontend
- Three-stage validation catches errors at every level
- dependency-cruiser enforces all boundary rules in CI
- Shared vue-common library (libs/vue-common) provides base components across all apps
