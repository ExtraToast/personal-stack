<script setup lang="ts">
import { Card, SubmitButton, useMutationState, useToast } from '@personal-stack/vue-common'
import { computed, onMounted, ref, watch } from 'vue'
import { useChatSessionsStore } from '../stores/chatSessions'

const store = useChatSessionsStore()
const toast = useToast()

const draft = ref('')
const newTitle = ref('')

const send = useMutationState<void>()
const create = useMutationState<void>()

const active = computed(() => {
  const id = store.activeSessionId
  return id ? (store.detailById[id] ?? null) : null
})

onMounted(async () => {
  try {
    await store.loadAll()
  } catch (e) {
    toast.error('Could not load chat sessions', e instanceof Error ? e.message : String(e))
  }
})

watch(
  () => store.sessions.length,
  async (next) => {
    // Auto-open the most-recent session on first load if none is selected.
    if (next > 0 && !store.activeSessionId) {
      try {
        await store.open(store.sessions[0]!.id)
      } catch (e) {
        toast.error('Could not open the chat', e instanceof Error ? e.message : String(e))
      }
    }
  },
  { immediate: true },
)

async function onCreate(): Promise<void> {
  try {
    await create.run(async () => {
      const trimmed = newTitle.value.trim()
      const session = await store.start(trimmed ? { title: trimmed } : {})
      await store.open(session.id)
      newTitle.value = ''
    })
  } catch (e) {
    toast.error('Could not start a chat session', e instanceof Error ? e.message : String(e))
  }
}

async function onSend(): Promise<void> {
  const body = draft.value.trim()
  const sessionId = store.activeSessionId
  if (!body || !sessionId) return
  try {
    await send.run(async () => {
      await store.send(sessionId, { body, role: 'USER' })
    })
    draft.value = ''
  } catch (e) {
    toast.error('Could not send message', e instanceof Error ? e.message : String(e))
  }
}

async function onSelect(id: string): Promise<void> {
  try {
    await store.open(id)
  } catch (e) {
    toast.error('Could not open the chat', e instanceof Error ? e.message : String(e))
  }
}
</script>

<template>
  <div class="grid gap-6 lg:grid-cols-[18rem,1fr]" data-testid="chat-tab">
    <aside class="space-y-3">
      <form class="flex gap-2" @submit.prevent="onCreate">
        <input
          v-model="newTitle"
          type="text"
          maxlength="120"
          placeholder="New chat title (optional)"
          class="flex-1 rounded border border-[var(--color-surface-border)] bg-[var(--color-surface-card)] px-3 py-1.5 text-sm"
          data-testid="chat-new-title"
        />
        <SubmitButton label="Start" :status="create.status.value" data-testid="chat-new-submit" />
      </form>

      <p v-if="store.sessions.length === 0" class="text-sm text-gray-500 italic">
        No chats yet. Start one above — chat sessions have no Pod, just LLM Q&A against the knowledge base.
      </p>

      <ul v-else class="space-y-2" data-testid="chat-sessions-list">
        <li v-for="s in store.sessions" :key="s.id">
          <button
            type="button"
            class="w-full rounded border px-3 py-2 text-left text-sm transition-colors"
            :class="[
              s.id === store.activeSessionId
                ? 'border-[var(--color-accent)] bg-[var(--color-surface-elevated)]'
                : 'border-[var(--color-surface-border)] bg-[var(--color-surface-card)] hover:border-[var(--color-accent)]',
            ]"
            :data-testid="`chat-session-${s.id}`"
            @click="onSelect(s.id)"
          >
            <p class="font-medium">{{ s.title ?? 'Untitled chat' }}</p>
            <p class="text-xs text-gray-500">{{ new Date(s.updatedAt).toLocaleString() }}</p>
          </button>
        </li>
      </ul>
    </aside>

    <section class="space-y-4">
      <Card v-if="active" :data-testid="`chat-detail-${active.session.id}`">
        <template #header>
          <h2 class="text-lg font-semibold">{{ active.session.title ?? 'Untitled chat' }}</h2>
        </template>

        <div
          v-if="active.messages.length === 0"
          class="rounded border border-dashed border-[var(--color-surface-border)] p-6 text-center text-sm text-gray-500"
        >
          No messages yet. Say hi.
        </div>
        <ul v-else class="space-y-2" data-testid="chat-message-list">
          <li
            v-for="m in active.messages"
            :key="m.id"
            class="rounded-md px-3 py-2 text-sm"
            :class="[
              m.role === 'USER'
                ? 'bg-[var(--color-surface-elevated)] text-gray-100'
                : 'bg-[var(--color-surface-card)] text-gray-200 border border-[var(--color-surface-border)]',
            ]"
            :data-testid="`chat-message-${m.id}`"
          >
            <p class="text-xs text-gray-500 mb-1">{{ m.role === 'USER' ? 'You' : 'Assistant' }}</p>
            <p class="whitespace-pre-wrap">{{ m.body }}</p>
          </li>
        </ul>

        <template #footer>
          <form class="flex gap-2" @submit.prevent="onSend">
            <textarea
              v-model="draft"
              rows="2"
              placeholder="Send a message…"
              class="flex-1 rounded border border-[var(--color-surface-border)] bg-[var(--color-surface-card)] px-3 py-2 text-sm"
              data-testid="chat-input"
            />
            <SubmitButton
              label="Send"
              :status="send.status.value"
              :disabled="!draft.trim()"
              data-testid="chat-send-submit"
            />
          </form>
        </template>
      </Card>

      <div
        v-else
        class="rounded-lg border border-dashed border-[var(--color-surface-border)] p-8 text-center text-sm text-gray-500"
      >
        Pick a chat from the sidebar — or start a new one to open it.
      </div>
    </section>
  </div>
</template>
