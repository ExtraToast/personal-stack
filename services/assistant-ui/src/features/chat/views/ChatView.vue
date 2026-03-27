<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { useConversationStore } from '@/stores/conversation'
import ChatInput from '../components/ChatInput.vue'
import ConversationList from '../components/ConversationList.vue'

const store = useConversationStore()
const messagesEndRef = ref<HTMLElement | null>(null)

onMounted(async () => {
  await store.loadConversations()
})

async function scrollToBottom(): Promise<void> {
  await nextTick()
  messagesEndRef.value?.scrollIntoView({ behavior: 'smooth' })
}

watch(() => store.messages.length, scrollToBottom)

async function handleSend(content: string): Promise<void> {
  await store.send(content)
}
</script>

<template>
  <div class="flex h-screen bg-surface-dark">
    <ConversationList />

    <div class="flex flex-1 flex-col overflow-hidden">
      <!-- Header -->
      <header class="flex items-center border-b border-surface-border bg-surface-card px-6 py-4">
        <h1 class="font-mono text-sm font-semibold text-gray-200">
          <span class="text-gray-600">~/chat/</span>
          <span class="text-terminal-green">
            {{ store.activeConversation()?.title ?? 'Select a conversation' }}
          </span>
        </h1>
      </header>

      <!-- Messages -->
      <main class="flex-1 overflow-y-auto px-6 py-4">
        <div v-if="!store.activeConversationId" class="flex h-full items-center justify-center">
          <!-- prettier-ignore -->
          <p class="font-mono text-sm text-gray-600">
            Select a conversation or start a new one
          </p>
        </div>

        <div v-else class="space-y-4">
          <div
            v-for="msg in store.messages"
            :key="msg.id"
            class="flex"
            :class="[msg.role === 'USER' ? 'justify-end' : 'justify-start']"
          >
            <div
              class="max-w-lg rounded-lg px-4 py-2 text-sm"
              :class="[
                msg.role === 'USER'
                  ? 'bg-accent text-white'
                  : 'border border-surface-border bg-surface-elevated text-gray-300',
              ]"
            >
              <p class="whitespace-pre-wrap">
                {{ msg.content }}
              </p>
              <time class="mt-1 block font-mono text-xs opacity-50">
                {{ new Date(msg.createdAt).toLocaleTimeString() }}
              </time>
            </div>
          </div>

          <div v-if="store.isSending" class="flex justify-start">
            <!-- prettier-ignore -->
            <div
              class="rounded-lg border border-surface-border bg-surface-elevated
                px-4 py-2 font-mono text-sm text-gray-500"
            >
              Thinking...
            </div>
          </div>

          <div ref="messagesEndRef" />
        </div>
      </main>

      <!-- Input -->
      <ChatInput v-if="store.activeConversationId" :disabled="store.isSending" @send="handleSend" />
    </div>
  </div>
</template>
