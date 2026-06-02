<script setup lang="ts">
import { Card, Modal, SubmitButton, useMutationState, useToast } from '@personal-stack/vue-common'
import { computed, onMounted, ref } from 'vue'
import { useWorkspacesStore } from '@/features/workspaces'
import CreateWorkspaceWizard from './CreateWorkspaceWizard.vue'

const store = useWorkspacesStore()
const toast = useToast()
const showWizard = ref(false)
const destroy = useMutationState<void>()
const deletingId = ref<string | null>(null)

const repoBackedWorkspaces = computed(() => store.workspaces.filter((w) => w.kind === 'REPO_BACKED'))

onMounted(() => {
  void store.loadAll()
})

async function onDestroy(id: string, name: string): Promise<void> {
  // See ProjectView for the rationale on using window.confirm.
  // eslint-disable-next-line no-alert
  if (
    !window.confirm(
      `Delete workspace "${name}"? Its Pod, volume, and agent sessions are torn down. This cannot be undone.`,
    )
  ) {
    return
  }
  deletingId.value = id
  try {
    await destroy.run(async () => {
      await store.destroy(id)
    })
    toast.success('Workspace deleted')
  } catch (e) {
    toast.errorFromCatch('Could not delete the workspace', e)
  } finally {
    deletingId.value = null
  }
}
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
        <Card :to="`/sessions/workspace/${w.id}`" :data-testid="`workspace-${w.id}`">
          <template #header>
            <div class="flex items-baseline justify-between">
              <span class="font-semibold">{{ w.name }}</span>
              <span class="text-xs text-gray-500">{{ w.status }}</span>
            </div>
          </template>
          <p v-if="w.repoUrl" class="font-mono text-xs text-gray-400">{{ w.repoUrl }}</p>
          <p v-if="w.branch" class="text-xs text-gray-500">branch: {{ w.branch }}</p>
          <div class="mt-3 flex justify-end">
            <SubmitButton
              type="button"
              variant="danger"
              label="Delete"
              :status="deletingId === w.id ? destroy.status.value : 'idle'"
              :data-testid="`workspace-delete-${w.id}`"
              @click.stop.prevent="onDestroy(w.id, w.name)"
            />
          </div>
        </Card>
      </li>
    </ul>

    <Modal :open="showWizard" title="Open a workspace" @close="showWizard = false">
      <CreateWorkspaceWizard :open="showWizard" @close="showWizard = false" @created="showWizard = false" />
    </Modal>
  </div>
</template>
