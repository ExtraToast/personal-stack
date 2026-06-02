// Types for the repositories feature.
//
// Wire shapes (Repository, CreateRepositoryInput, AttachDeployKeyInput)
// flow through the OpenAPI contract pinned at
// `services/assistant-api/openapi.json` and the regenerated
// `src/api/generated.ts`. Drift against the assistant-api DTOs is
// detected by `pnpm contract:check` in CI — see
// `services/assistant-api/CONTRACT.md`.
//
// `RepositoryDetail` + `AttachedProject` stay hand-rolled until the
// backend's `GET /repositories/{id}` returns a typed response body
// (today it serialises as `Map<String, Any>`, which springdoc emits
// as `object` without a schema).

import type { components } from '@/api/generated'

export type Repository = components['schemas']['RepositoryResponse']
export type CreateRepositoryInput = components['schemas']['CreateRepositoryRequest']
export type AttachDeployKeyInput = components['schemas']['AttachRepositoryDeployKeyRequest']

// Deploy-key access verification result. Layer 2 (assistant-api) adds
// these fields to `GET /repositories/{id}` and a `POST
// /repositories/{id}/verify`. Until that contract lands on main and the
// OpenAPI types regenerate, this stays hand-rolled.
// TODO: switch to `components['schemas']['RepositoryVerifyResponse']`
// once Layer 2's verify endpoint is in `src/api/generated.ts`.
export interface RepositoryVerifyResult {
  read: boolean
  write: boolean
  // null = no GitHub token configured or the protection check was
  // inconclusive (never a hard failure).
  defaultBranchProtected: boolean | null
  checkedAt: string
  messages: string[]
}

export interface RepositoryDetail {
  repository: Repository
  attachedProjects: AttachedProject[]
  // Present once the backend has run (or cached) a verification pass.
  // Absent on repositories that have never been verified.
  verify?: RepositoryVerifyResult | null
}

export interface AttachedProject {
  id: string
  name: string
  slug: string
}
