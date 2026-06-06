<script setup lang="ts">
import type { WorkspaceRepository } from '../types'
import type { Repository } from '@/features/repositories'
import { useToast } from '@personal-stack/vue-common'
import { computed, onMounted, ref } from 'vue'
import { useRepositoriesStore } from '@/features/repositories'

interface Props {
  alreadyAttached: WorkspaceRepository[]
  pending?: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  pick: [repositoryId: string]
  cancel: []
}>()

const store = useRepositoriesStore()
const toast = useToast()
const selected = ref('')

onMounted(async () => {
  try {
    await store.loadAll()
  } catch (e) {
    toast.errorFromCatch('Could not load repositories', e)
  }
})

const candidates = computed<Repository[]>(() => {
  const attachedIds = new Set(props.alreadyAttached.map((r) => r.id))
  return store.items.filter((r) => !attachedIds.has(r.id))
})

function onSubmit(): void {
  if (!selected.value || props.pending) return
  emit('pick', selected.value)
}
</script>

<template>
  <div class="space-y-4" data-testid="workspace-repository-picker">
    <p class="text-sm text-[var(--color-text-muted)]">
      Attach an existing repository to this workspace. It becomes available to future runner starts for this workspace,
      but the primary clone stays unchanged.
      <RouterLink to="/repositories" class="text-[var(--color-accent-light)] underline">Add a repository</RouterLink>
      first if it is missing.
    </p>

    <div v-if="store.isLoading && store.items.length === 0" class="text-sm text-[var(--color-text-muted)]">
      Loading repositories...
    </div>

    <p v-else-if="candidates.length === 0" class="text-sm text-[var(--color-text-muted)] italic">
      Every repository is already attached to this workspace, or you have not created any yet.
    </p>

    <ul v-else class="max-h-72 space-y-2 overflow-y-auto">
      <li v-for="r in candidates" :key="r.id">
        <label
          class="flex cursor-pointer items-baseline gap-3 rounded-md border border-[var(--color-surface-border)] bg-[var(--color-surface-card)] p-3 hover:border-[var(--color-accent)]"
        >
          <input
            v-model="selected"
            type="radio"
            :value="r.id"
            class="mt-1"
            :disabled="pending"
            :data-testid="`repository-picker-radio-${r.id}`"
          />
          <div class="min-w-0 flex-1">
            <div class="flex items-baseline justify-between gap-3">
              <span class="font-semibold">{{ r.name }}</span>
              <span v-if="!r.deployKeyFingerprint" class="shrink-0 text-xs text-amber-400">no key yet</span>
              <span v-else class="shrink-0 text-xs text-emerald-400">key attached</span>
            </div>
            <p class="mt-1 truncate font-mono text-xs text-[var(--color-text-muted)]">{{ r.repoUrl }}</p>
          </div>
        </label>
      </li>
    </ul>

    <div class="flex justify-end gap-2">
      <button
        type="button"
        class="rounded border border-[var(--color-surface-border)] px-4 py-2 text-sm disabled:cursor-not-allowed disabled:opacity-60"
        :disabled="pending"
        @click="emit('cancel')"
      >
        Cancel
      </button>
      <button
        type="button"
        class="rounded bg-blue-600 px-4 py-2 text-sm text-white disabled:cursor-not-allowed disabled:opacity-60"
        :disabled="!selected || pending"
        data-testid="repository-picker-submit"
        @click="onSubmit"
      >
        {{ pending ? 'Attaching...' : 'Attach repository' }}
      </button>
    </div>
  </div>
</template>
