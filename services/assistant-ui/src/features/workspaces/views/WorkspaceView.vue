<script setup lang="ts">
import type { AgentKind } from '../types'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AgentKindPicker from '../components/AgentKindPicker.vue'
import SessionTabs from '../components/SessionTabs.vue'
import SessionTerminal from '../components/SessionTerminal.vue'
import { useWorkspacesStore } from '../stores/workspaces'

const route = useRoute()
const router = useRouter()
const store = useWorkspacesStore()

const workspaceId = computed(() => String(route.params.id))
const pickerKind = ref<AgentKind>('CLAUDE')

onMounted(async () => {
  await store.open(workspaceId.value)
})

async function onSpawn(): Promise<void> {
  await store.newSession(pickerKind.value)
}

async function onStopSession(id: string): Promise<void> {
  await store.endSession(id)
}
</script>

<template>
  <div class="flex flex-col h-screen">
    <header class="border-b border-[var(--color-surface-border)] px-6 py-3 flex items-center justify-between">
      <div>
        <button
          type="button"
          class="text-sm text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)] mb-1"
          @click="router.push('/sessions')"
        >
          ← Sessions
        </button>
        <h1 class="text-xl font-bold">
          {{ store.activeWorkspace?.name ?? 'Loading…' }}
        </h1>
        <p v-if="store.activeWorkspace?.repoUrl" class="text-xs text-[var(--color-text-muted)] font-mono">
          {{ store.activeWorkspace.repoUrl }}
        </p>
      </div>
      <div class="flex items-center gap-2">
        <AgentKindPicker v-model="pickerKind" />
        <button
          type="button"
          class="rounded bg-blue-600 hover:bg-blue-700 px-3 py-2 text-sm text-white"
          @click="onSpawn"
        >
          New agent
        </button>
      </div>
    </header>

    <SessionTabs
      :sessions="store.sessions"
      :active-id="store.activeSessionId"
      @select="(id) => (store.activeSessionId = id)"
      @stop="onStopSession"
    />

    <main class="flex flex-col flex-1 overflow-hidden p-4 gap-3">
      <SessionTerminal v-if="store.activeSessionId" :key="store.activeSessionId" :session-id="store.activeSessionId" />
      <div v-else class="text-center text-[var(--color-text-muted)] italic py-4">
        Pick an agent kind above and click "New agent" to start.
      </div>
    </main>
  </div>
</template>
