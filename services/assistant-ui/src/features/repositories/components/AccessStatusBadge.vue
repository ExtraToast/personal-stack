<script setup lang="ts">
import type { RepositoryVerifyResult } from '../types'
import { computed } from 'vue'

interface Props {
  verify: RepositoryVerifyResult | null | undefined
}

const props = defineProps<Props>()

interface Pill {
  testid: string
  label: string
  tone: 'ok' | 'warn' | 'fail' | 'unknown'
}

const pills = computed<Pill[]>(() => {
  const v = props.verify
  if (!v) return []
  const out: Pill[] = []

  out.push(
    v.read
      ? { testid: 'access-read', label: 'Read OK', tone: 'ok' }
      : { testid: 'access-read', label: 'Read failed', tone: 'fail' },
  )

  out.push(
    v.write
      ? { testid: 'access-write', label: 'Write OK', tone: 'ok' }
      : // read-only is the safe default, not an error.
        { testid: 'access-write', label: 'Read-only', tone: 'warn' },
  )

  if (v.defaultBranchProtected === true)
    out.push({ testid: 'access-protection', label: 'Branch protected', tone: 'ok' })
  else if (v.defaultBranchProtected === false)
    out.push({ testid: 'access-protection', label: 'Branch unprotected', tone: 'warn' })
  else
    // null = no GitHub token configured / inconclusive.
    out.push({ testid: 'access-protection', label: 'Protection unknown', tone: 'unknown' })

  return out
})

const toneClass: Record<Pill['tone'], string> = {
  ok: 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30',
  warn: 'bg-amber-500/15 text-amber-300 border-amber-500/30',
  fail: 'bg-red-500/15 text-red-300 border-red-500/30',
  unknown: 'bg-white/5 text-[var(--color-text-muted)] border-[var(--color-surface-border)]',
}
</script>

<template>
  <div v-if="pills.length > 0" class="flex flex-wrap items-center gap-2" data-testid="access-status-badge">
    <span
      v-for="pill in pills"
      :key="pill.testid"
      class="inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-medium"
      :class="toneClass[pill.tone]"
      :data-testid="pill.testid"
      :data-tone="pill.tone"
    >
      {{ pill.label }}
    </span>
  </div>
  <p v-else class="text-xs text-[var(--color-text-muted)]" data-testid="access-status-unverified">
    Access not verified yet.
  </p>
</template>
