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

export interface RepositoryDetail {
  repository: Repository
  attachedProjects: AttachedProject[]
}

export interface AttachedProject {
  id: string
  name: string
  slug: string
}
