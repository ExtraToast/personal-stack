<script setup lang="ts">
import type { WorkspaceRepository } from '../types'
import { computed } from 'vue'

interface Props {
  repositories: WorkspaceRepository[]
  attachPending?: boolean
  detachPendingId?: string | null
  error?: string | null
}

const props = defineProps<Props>()

const emit = defineEmits<{
  add: []
  detach: [repositoryId: string, repositoryName: string]
}>()

const sortedRepositories = computed(() =>
  [...props.repositories].sort((a, b) => {
    if (a.isPrimary !== b.isPrimary) return a.isPrimary ? -1 : 1
    return a.name.localeCompare(b.name)
  }),
)
</script>

<template>
  <section
    class="rounded-md border border-[var(--color-surface-border)] bg-[var(--color-surface-card)] p-4"
    data-testid="workspace-repositories-panel"
  >
    <div class="mb-3 flex items-start justify-between gap-3">
      <div>
        <h2 class="text-sm font-semibold">Repositories</h2>
        <p class="mt-1 text-xs text-[var(--color-text-muted)]">
          Attached repositories are available to this workspace. The primary repository is the runner clone.
        </p>
      </div>
      <button
        type="button"
        class="shrink-0 rounded bg-blue-600 px-3 py-2 text-xs text-white disabled:cursor-not-allowed disabled:opacity-60"
        :disabled="attachPending"
        data-testid="workspace-add-repository"
        @click="emit('add')"
      >
        {{ attachPending ? 'Attaching...' : 'Attach' }}
      </button>
    </div>

    <p v-if="error" class="mb-3 rounded border border-red-500/40 bg-red-500/5 p-2 text-xs text-red-300">
      {{ error }}
    </p>

    <p v-if="sortedRepositories.length === 0" class="text-sm text-[var(--color-text-muted)] italic">
      No repositories attached.
    </p>

    <ul v-else class="space-y-3" data-testid="workspace-repositories-list">
      <li
        v-for="r in sortedRepositories"
        :key="r.id"
        class="rounded-md border border-[var(--color-surface-border)] bg-[var(--color-surface)] p-3"
        :data-testid="`workspace-repository-${r.id}`"
      >
        <div class="flex items-start justify-between gap-3">
          <div class="min-w-0">
            <div class="flex flex-wrap items-center gap-2">
              <RouterLink :to="`/repositories/${r.id}`" class="font-semibold hover:underline">
                {{ r.name }}
              </RouterLink>
              <span
                v-if="r.isPrimary"
                class="rounded border border-blue-400/50 px-1.5 py-0.5 text-[10px] uppercase tracking-wide text-blue-300"
                :data-testid="`workspace-repository-primary-${r.id}`"
              >
                primary
              </span>
              <span v-if="!r.deployKeyFingerprint" class="text-xs text-amber-400">no key yet</span>
              <span v-else class="text-xs text-emerald-400">key attached</span>
            </div>
            <p class="mt-1 truncate font-mono text-xs text-[var(--color-text-muted)]">{{ r.repoUrl }}</p>
            <p class="mt-1 text-xs text-[var(--color-text-muted)]">default: {{ r.defaultBranch }}</p>
          </div>

          <button
            v-if="!r.isPrimary"
            type="button"
            class="shrink-0 rounded border border-red-500/60 px-2.5 py-1.5 text-xs text-red-300 disabled:cursor-not-allowed disabled:opacity-60"
            :disabled="detachPendingId === r.id"
            :data-testid="`workspace-detach-repository-${r.id}`"
            @click="emit('detach', r.id, r.name)"
          >
            {{ detachPendingId === r.id ? 'Removing...' : 'Remove' }}
          </button>
        </div>
      </li>
    </ul>
  </section>
</template>
