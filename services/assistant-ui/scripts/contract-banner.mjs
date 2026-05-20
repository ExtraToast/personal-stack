#!/usr/bin/env node
// Prepends a short provenance banner to the openapi-typescript output.
// Kept in step between `contract:generate` and `contract:check` so the
// diff produced by the latter only flags real schema drift.

import { readFileSync, writeFileSync } from 'node:fs'
import process from 'node:process'

const target = process.argv[2]
if (!target) {
  console.error('usage: contract-banner.mjs <file>')
  process.exit(2)
}

const banner = `/**
 * AUTO-GENERATED. Do not edit by hand.
 *
 * Source: services/assistant-api/openapi.json (committed)
 * Regenerate with: pnpm --filter @personal-stack/assistant-ui contract:generate
 * Drift gate: pnpm --filter @personal-stack/assistant-ui contract:check
 * Contract docs: services/assistant-api/CONTRACT.md
 */
`

const existing = readFileSync(target, 'utf8')
writeFileSync(target, banner + existing)
