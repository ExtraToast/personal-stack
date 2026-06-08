# assistant-api ↔ assistant-ui OpenAPI contract

This service emits its OpenAPI 3.1 spec via springdoc at
`/api/v1/api-docs`. The spec is committed to
`services/assistant-api/openapi.json` and consumed by
`services/assistant-ui` through `openapi-typescript`-generated
`src/api/generated.ts`. CI fails any PR that lets the committed spec,
the live springdoc output, and the generated TypeScript drift apart.

## Pieces

- `services/assistant-api/openapi.json` — pinned spec (committed).
- `services/assistant-ui/src/api/generated.ts` — pinned TS types
  (committed; produced by `openapi-typescript` v7.4.4 plus the
  banner script).
- `OpenApiSpecExportTest` (integration tier, tag
  `contract-export`) — boots the full Spring context and dumps the
  spec to the committed path.
- Gradle task `:services:assistant-api:exportOpenApiSpec` —
  runs only the tagged test; the default `integrationTest` task
  excludes the tag.
- npm scripts in `services/assistant-ui/package.json`:
  - `contract:generate` — regenerate types from the committed spec.
  - `contract:check` — regenerate to `/tmp` and `diff -u` against
    the committed copy; non-zero exit on drift.
- `.github/workflows/contract-validate.yml` — runs both gates on
  every PR that touches assistant-api, assistant-ui, vue-common,
  published commons modules, or shared build config.

## Regenerate after an API change

```bash
./gradlew :services:assistant-api:exportOpenApiSpec
pnpm --filter @personal-stack/assistant-ui contract:generate
git add services/assistant-api/openapi.json \
        services/assistant-ui/src/api/generated.ts
```

Commit the two files alongside the controller / DTO change in the
same PR.

## What CI failures look like

- **`openapi.json` drift.** The Gradle export task in
  `contract-validate.yml` overwrites the committed file with the live
  springdoc output, then `git diff --exit-code` flags the change.
  Resolve by running `./gradlew :services:assistant-api:exportOpenApiSpec`
  locally and committing the result.
- **`generated.ts` drift.** `pnpm contract:check` regenerates the TS
  output to `/tmp` and `diff -u`s it against the committed copy. The
  CI log shows the patch. Resolve by running
  `pnpm --filter @personal-stack/assistant-ui contract:generate` and
  committing the new `src/api/generated.ts`.

## Migration status (PR H)

The repositories feature is the canary consumer:
`services/assistant-ui/src/features/repositories/types/index.ts` now
derives `Repository`, `CreateRepositoryInput`, and
`AttachDeployKeyInput` from `components['schemas']` in
`@/api/generated`. `RepositoryDetail` + `AttachedProject` stay
hand-rolled until `GET /api/v1/repositories/{id}` returns a typed
response body (the controller currently emits `Map<String, Any>`).
Further feature directories migrate in follow-up PRs once their DTOs
ship with concrete response types.
