<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CreateWorkspaceForm from '../components/CreateWorkspaceForm.vue'
import WorkspaceCard from '../components/WorkspaceCard.vue'
import { useWorkspacesStore } from '../stores/workspaces'

const store = useWorkspacesStore()
const router = useRouter()
const showCreate = ref(false)

onMounted(() => {
  void store.loadAll()
})

async function onCreate(input: { name: string; repoUrl: string | null; branch: string | null; githubLinkId: string | null }): Promise<void> {
  const ws = await store.create(input)
  showCreate.value = false
  void router.push(`/workspaces/${ws.id}`)
}

function onOpen(id: string): void {
  void router.push(`/workspaces/${id}`)
}

async function onDestroy(id: string): Promise<void> {
  if (!window.confirm('Destroy this workspace? The runner Pod and PVC will be deleted.')) return
  await store.destroy(id)
}
</script>

<template>
  <div class="max-w-5xl mx-auto p-6">
    <header class="flex items-center justify-between mb-6">
      <h1 class="text-2xl font-bold">Workspaces</h1>
      <button
        type="button"
        class="rounded bg-blue-600 hover:bg-blue-700 px-4 py-2 text-sm text-white"
        @click="showCreate = !showCreate"
      >
        {{ showCreate ? 'Cancel' : 'New workspace' }}
      </button>
    </header>

    <section v-if="showCreate" class="mb-8 rounded-lg border border-gray-700 bg-surface-darker p-4">
      <CreateWorkspaceForm @submit="onCreate" @cancel="showCreate = false" />
    </section>

    <p v-if="store.error" class="text-red-400 mb-4">{{ store.error }}</p>

    <div v-if="store.isLoading" class="text-gray-400">Loading…</div>
    <div v-else-if="store.workspaces.length === 0" class="text-gray-500 italic">
      No workspaces yet. Spin one up to start working with Claude or Codex.
    </div>
    <div v-else class="grid grid-cols-1 md:grid-cols-2 gap-4">
      <WorkspaceCard
        v-for="ws in store.workspaces"
        :key="ws.id"
        :workspace="ws"
        @open="onOpen"
        @destroy="onDestroy"
      />
    </div>
  </div>
</template>
