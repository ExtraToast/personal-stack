<script setup lang="ts">
import type { SessionSocket } from '../services/sessionSocket'
import type { AgentKind } from '../types'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AgentKindPicker from '../components/AgentKindPicker.vue'
import SessionInput from '../components/SessionInput.vue'
import SessionTabs from '../components/SessionTabs.vue'
import SessionTranscript from '../components/SessionTranscript.vue'
import { attachSessionSocket } from '../services/sessionSocket'
import { sendInput } from '../services/workspaceService'
import { useWorkspacesStore } from '../stores/workspaces'

const route = useRoute()
const router = useRouter()
const store = useWorkspacesStore()

const workspaceId = computed(() => String(route.params.id))
const pickerKind = ref<AgentKind>('CLAUDE')
const socket = ref<SessionSocket | null>(null)

onMounted(async () => {
  await store.open(workspaceId.value)
  await maybeAttach()
})

onBeforeUnmount(() => {
  socket.value?.close()
  socket.value = null
})

watch(
  () => store.activeSessionId,
  async (id) => {
    socket.value?.close()
    socket.value = null
    if (id) {
      await store.loadTurns(id)
      await maybeAttach()
    }
  },
)

async function maybeAttach(): Promise<void> {
  const id = store.activeSessionId
  if (!id) return
  socket.value = attachSessionSocket({
    sessionId: id,
    onOutput: (text) => store.appendStreamedOutput(text),
    onClose: () => {
      /* allow component to handle reconnect */
    },
  })
}

async function onSpawn(): Promise<void> {
  await store.newSession(pickerKind.value)
}

async function onSend(text: string): Promise<void> {
  if (!socket.value || socket.value.readyState() !== WebSocket.OPEN) {
    // Fallback: REST send if the WS hasn't opened yet.
    const sessionId = store.activeSessionId
    if (!sessionId) return
    await sendInput(workspaceId.value, sessionId, text)
    store.appendUserTurn(text)
    return
  }
  socket.value.send(text)
  store.appendUserTurn(text)
}

async function onStopSession(id: string): Promise<void> {
  await store.endSession(id)
}

/**
 * Synthesise a user input from a Block interaction. The agent
 * receives the same text it would if the operator had typed the
 * reply by hand — keeping the gateway / CLI side stateless about
 * blocks. Each kind has its own deterministic wire form so the
 * agent's parser can pattern-match on it.
 */
async function onBlockPick(value: { sessionId: string; optionId: string }): Promise<void> {
  await onSend(`/choose ${value.optionId}`)
}

async function onBlockDecide(value: { sessionId: string; approved: boolean }): Promise<void> {
  await onSend(value.approved ? '/approve' : '/reject')
}

async function onBlockFormSubmit(value: { sessionId: string; data: Record<string, unknown> }): Promise<void> {
  await onSend(`/form ${JSON.stringify(value.data)}`)
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
      <SessionTranscript
        :turns="store.turns"
        @pick="onBlockPick"
        @decide="onBlockDecide"
        @form-submit="onBlockFormSubmit"
      />
      <SessionInput v-if="store.activeSessionId" @submit="onSend" />
      <div v-else class="text-center text-[var(--color-text-muted)] italic py-4">
        Pick an agent kind above and click "New agent" to start.
      </div>
    </main>
  </div>
</template>
