<script setup lang="ts">
import { Card, Modal, SubmitButton } from '@personal-stack/vue-common'
import { computed, onMounted, ref } from 'vue'
import { useWorkspacesStore } from '@/features/workspaces/stores/workspaces'
import CreateWorkspaceWizard from './CreateWorkspaceWizard.vue'

const store = useWorkspacesStore()
const showWizard = ref(false)

const repoBackedWorkspaces = computed(() => store.workspaces.filter((w) => w.kind === 'REPO_BACKED'))

onMounted(() => {
  void store.loadAll()
})
</script>

<template>
  <div class="space-y-6" data-testid="workspace-tab">
    <header class="flex items-center justify-between">
      <div>
        <h2 class="text-lg font-semibold">Repo-backed workspaces</h2>
        <p class="mt-1 text-sm text-gray-400">
          One Pod per workspace, each with the project's repository cloned and the agent CLIs ready to go.
        </p>
      </div>
      <SubmitButton type="button" label="New workspace" data-testid="workspace-new-button" @click="showWizard = true" />
    </header>

    <p v-if="repoBackedWorkspaces.length === 0" class="text-sm text-gray-500 italic">
      No repo-backed workspaces yet. Open one — the wizard walks you through picking a project + repository.
    </p>

    <ul v-else class="grid gap-3 sm:grid-cols-2" data-testid="workspace-list">
      <li v-for="w in repoBackedWorkspaces" :key="w.id">
        <Card :to="`/workspaces/${w.id}`" :data-testid="`workspace-${w.id}`">
          <template #header>
            <div class="flex items-baseline justify-between">
              <span class="font-semibold">{{ w.name }}</span>
              <span class="text-xs text-gray-500">{{ w.status }}</span>
            </div>
          </template>
          <p v-if="w.repoUrl" class="font-mono text-xs text-gray-400">{{ w.repoUrl }}</p>
          <p v-if="w.branch" class="text-xs text-gray-500">branch: {{ w.branch }}</p>
        </Card>
      </li>
    </ul>

    <Modal :open="showWizard" title="Open a workspace" @close="showWizard = false">
      <CreateWorkspaceWizard :open="showWizard" @close="showWizard = false" @created="showWizard = false" />
    </Modal>
  </div>
</template>
