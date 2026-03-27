# ADR-011: Frontend Technology Stack

## Status

Accepted

## Date

2026-03-25

## Context

We need to choose specific tools and versions for the Vue frontend applications. The stack must have excellent
TypeScript support and be consistent across all frontend apps.

## Decision

### Core Stack

| Component  | Version      | Notes                                  |
| ---------- | ------------ | -------------------------------------- |
| Vue        | 3.x (latest) | Composition API only                   |
| TypeScript | 5.x (latest) | Strict mode                            |
| Vite       | Latest       | Build tool                             |
| pnpm       | Latest       | Package manager + workspace management |

### State & Routing

- **Pinia** — official Vue store, excellent TypeScript support
- **Vue Router 4** — standard routing

### UI & Styling

- **PrimeVue** — rich component library with unstyled mode
- **Tailwind CSS** — utility-first CSS, pairs well with PrimeVue unstyled mode

### Monorepo Structure

- **pnpm workspaces** for frontend package management
- Workspace packages:
  - services/auth-ui
  - services/assistant-ui
  - services/app-ui
  - libs/vue-common (shared components, composables, types)

### Shared Library (libs/vue-common)

Contains:

- Base UI components (buttons, forms, layouts)
- Auth composables (useAuth, usePermissions)
- HTTP client configuration
- Shared TypeScript types
- Error handling utilities
- Tailwind preset/theme

## Consequences

- PrimeVue provides 90+ components out of the box — less custom component building
- Tailwind + PrimeVue unstyled mode gives design flexibility with component functionality
- pnpm workspaces enforce strict dependency resolution — no phantom dependencies
- Shared vue-common library keeps all apps visually and functionally consistent
- All apps share the same Vite configuration base via vue-common
