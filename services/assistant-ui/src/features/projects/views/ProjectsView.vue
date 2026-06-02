<script setup lang="ts">
import type { Project } from '../types'
import { Card, Modal, useToast } from '@personal-stack/vue-common'
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CreateProjectForm from '../components/CreateProjectForm.vue'
import { useProjectsStore } from '../stores/projects'

const store = useProjectsStore()
const router = useRouter()
const toast = useToast()
const showCreate = ref(false)

onMounted(() => {
  void store.loadAll()
})

async function onCreate(input: { name: string; slug: string; description: string }): Promise<Project> {
  return store.create(input)
}

async function onCreateSuccess(created: Project): Promise<void> {
  showCreate.value = false
  toast.success('Project created', 'Link a repository next to open workspaces against it.')
  await router.push(`/projects/${created.id}`)
}
</script>

<template>
  <div class="max-w-5xl mx-auto p-6">
    <header class="mb-6 flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold">Projects</h1>
        <p class="mt-1 text-sm text-[var(--color-text-muted)]">
          A project groups one or more repositories. The same repository can live in multiple projects without uploading
          its deploy key twice.
        </p>
      </div>
      <button
        type="button"
        class="rounded bg-[var(--color-accent)] hover:bg-[var(--color-accent-light)] px-4 py-2 text-sm font-medium text-white"
        data-testid="projects-new-button"
        @click="showCreate = true"
      >
        New project
      </button>
    </header>

    <p v-if="store.error" class="mb-4 text-sm text-red-400">{{ store.error }}</p>

    <div v-if="store.isLoading && store.projects.length === 0" class="text-[var(--color-text-muted)]">Loading…</div>

    <div
      v-else-if="store.projects.length === 0"
      class="rounded-lg border border-dashed border-[var(--color-surface-border)] p-8 text-center"
    >
      <p class="text-[var(--color-text-muted)]">
        No projects yet.
        <button class="text-[var(--color-accent-light)] underline" @click="showCreate = true">Create one</button>
        to start grouping repositories.
      </p>
    </div>

    <ul v-else class="space-y-3" data-testid="projects-list">
      <li v-for="p in store.projects" :key="p.id">
        <Card :to="`/projects/${p.id}`" :data-testid="`project-${p.id}`">
          <template #header>
            <div class="flex items-baseline justify-between">
              <h3 class="font-semibold">{{ p.name }}</h3>
              <span class="text-xs font-mono text-[var(--color-text-muted)]">project:{{ p.slug }}</span>
            </div>
          </template>
          <p v-if="p.description" class="text-sm text-[var(--color-text-muted)]">{{ p.description }}</p>
        </Card>
      </li>
    </ul>

    <Modal :open="showCreate" title="Create project" @close="showCreate = false">
      <CreateProjectForm :on-submit="onCreate" @success="onCreateSuccess" @cancel="showCreate = false" />
    </Modal>
  </div>
</template>
