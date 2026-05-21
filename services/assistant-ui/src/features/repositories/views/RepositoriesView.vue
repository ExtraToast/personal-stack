<script setup lang="ts">
import type { CreateRepositoryInput, Repository } from '../types'
import { Card, Modal, useToast } from '@personal-stack/vue-common'
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CreateRepositoryForm from '../components/CreateRepositoryForm.vue'
import { useRepositoriesStore } from '../stores/repositories'

const store = useRepositoriesStore()
const router = useRouter()
const toast = useToast()

const showCreate = ref(false)

onMounted(() => {
  void store.loadAll()
})

// The form (CreateRepositoryForm) now takes an awaited `onSubmit`
// function prop and renders its own inline `<FormErrors>` banner +
// per-field errors on rejection. The view stays thin: it forwards
// the call to the store and handles only the success path (close
// modal, success toast, navigate). Failures live on the form.
async function onCreate(input: CreateRepositoryInput): Promise<Repository> {
  return store.create(input)
}

async function onCreateSuccess(created: Repository): Promise<void> {
  showCreate.value = false
  toast.success('Repository created', `Attach a deploy key next so the agent can clone ${created.name}.`)
  await router.push(`/repositories/${created.id}`)
}
</script>

<template>
  <div class="max-w-5xl mx-auto p-6">
    <header class="mb-6 flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold">Repositories</h1>
        <p class="mt-1 text-sm text-gray-400">
          A repository is one GitHub repo plus its deploy key. Projects below combine repositories — you can reuse the
          same repository in multiple projects without uploading the key twice.
        </p>
      </div>
      <button
        type="button"
        class="rounded bg-[var(--color-accent)] hover:bg-[var(--color-accent-light)] px-4 py-2 text-sm font-medium text-white"
        data-testid="repositories-new-button"
        @click="showCreate = true"
      >
        New repository
      </button>
    </header>

    <p v-if="store.error" class="mb-4 text-sm text-red-400">{{ store.error }}</p>

    <div v-if="store.isLoading && store.items.length === 0" class="text-gray-400">Loading…</div>

    <div
      v-else-if="store.items.length === 0"
      class="rounded-lg border border-dashed border-[var(--color-surface-border)] p-8 text-center"
    >
      <p class="text-gray-400">
        No repositories yet.
        <button class="text-[var(--color-accent-light)] underline" @click="showCreate = true">Add one</button> to get
        started.
      </p>
    </div>

    <ul v-else class="space-y-3" data-testid="repositories-list">
      <li v-for="r in store.items" :key="r.id">
        <Card :to="`/repositories/${r.id}`" :data-testid="`repository-${r.id}`">
          <template #header>
            <div class="flex items-baseline justify-between">
              <h3 class="font-semibold">{{ r.name }}</h3>
              <span
                v-if="r.deployKeyFingerprint"
                class="text-xs text-emerald-400"
                :data-testid="`repository-${r.id}-key-attached`"
                >key attached</span
              >
              <span v-else class="text-xs text-amber-400" :data-testid="`repository-${r.id}-key-missing`">
                no key yet
              </span>
            </div>
          </template>
          <p class="font-mono text-xs text-gray-400">{{ r.repoUrl }}</p>
          <p class="mt-1 text-xs text-gray-500">default: {{ r.defaultBranch }}</p>
        </Card>
      </li>
    </ul>

    <Modal :open="showCreate" title="Create repository" @close="showCreate = false">
      <CreateRepositoryForm :on-submit="onCreate" @success="onCreateSuccess" @cancel="showCreate = false" />
    </Modal>
  </div>
</template>
