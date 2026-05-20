<script setup lang="ts">
import type { Repository } from '@/features/repositories'
import { SubmitButton, useMutationState, useToast } from '@personal-stack/vue-common'
import { computed, onMounted, ref } from 'vue'
import { useRepositoriesStore } from '@/features/repositories'

interface Props {
  /**
   * Repositories already linked to the active project. Filtered out
   * of the picker so the operator can't double-link.
   */
  alreadyLinked: Repository[]
}

const props = defineProps<Props>()

const emit = defineEmits<{
  pick: [repositoryId: string]
  cancel: []
}>()

const store = useRepositoriesStore()
const submit = useMutationState<void>()
const toast = useToast()
const selected = ref<string>('')

onMounted(async () => {
  try {
    await store.loadAll()
  } catch (e) {
    toast.errorFromCatch('Could not load repositories', e)
  }
})

const candidates = computed<Repository[]>(() => {
  const linkedIds = new Set(props.alreadyLinked.map((r) => r.id))
  return store.items.filter((r) => !linkedIds.has(r.id))
})

async function onSubmit(): Promise<void> {
  if (!selected.value) return
  await submit.run(async () => {
    emit('pick', selected.value)
  })
}
</script>

<template>
  <div class="space-y-4" data-testid="project-repository-picker">
    <p class="text-sm text-gray-400">
      Pick an existing repository to add to this project. A repository can be in multiple projects without duplicating
      its deploy key. Need a new one?
      <RouterLink to="/repositories" class="text-[var(--color-accent-light)] underline"> Add a repository </RouterLink>
      first.
    </p>

    <div v-if="store.isLoading && store.items.length === 0" class="text-sm text-gray-500">Loading repositories…</div>

    <p v-else-if="candidates.length === 0" class="text-sm text-gray-500 italic">
      Every repository is already linked to this project, or you haven't created any yet.
    </p>

    <ul v-else class="space-y-2 max-h-72 overflow-y-auto">
      <li v-for="r in candidates" :key="r.id">
        <label
          class="flex items-baseline gap-3 rounded-md border border-[var(--color-surface-border)] bg-[var(--color-surface-card)] p-3 cursor-pointer hover:border-[var(--color-accent)]"
        >
          <input v-model="selected" type="radio" :value="r.id" class="mt-1" :data-testid="`picker-radio-${r.id}`" />
          <div class="flex-1">
            <div class="flex items-baseline justify-between">
              <span class="font-semibold">{{ r.name }}</span>
              <span v-if="!r.deployKeyFingerprint" class="text-xs text-amber-400">no key yet</span>
            </div>
            <p class="text-xs text-gray-400 font-mono mt-1">{{ r.repoUrl }}</p>
          </div>
        </label>
      </li>
    </ul>

    <div class="flex justify-end gap-2">
      <SubmitButton
        type="button"
        variant="secondary"
        label="Cancel"
        :disabled="submit.pending.value"
        @click="emit('cancel')"
      />
      <SubmitButton
        type="button"
        label="Link repository"
        :status="submit.status.value"
        :disabled="!selected"
        data-testid="picker-submit"
        @click="onSubmit"
      />
    </div>
  </div>
</template>
