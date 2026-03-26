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
  <div class="flex h-screen dark:bg-gray-950">
    <ConversationList />

    <div class="flex flex-1 flex-col overflow-hidden">
      <!-- Header -->
      <header
        class="flex items-center border-b border-gray-200 bg-white px-6 py-4 dark:border-gray-800 dark:bg-gray-900"
      >
        <h1 class="text-lg font-semibold">
          {{ store.activeConversation()?.title ?? 'Select a conversation' }}
        </h1>
      </header>

      <!-- Messages -->
      <main class="flex-1 overflow-y-auto px-6 py-4">
        <div v-if="!store.activeConversationId" class="flex h-full items-center justify-center">
          <p class="text-gray-500 dark:text-gray-400">Select a conversation or start a new one</p>
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
                  ? 'bg-gray-900 text-white dark:bg-gray-100 dark:text-gray-900'
                  : 'bg-gray-100 text-gray-900 dark:bg-gray-800 dark:text-gray-100',
              ]"
            >
              <p class="whitespace-pre-wrap">
                {{ msg.content }}
              </p>
              <time class="mt-1 block text-xs opacity-60">
                {{ new Date(msg.createdAt).toLocaleTimeString() }}
              </time>
            </div>
          </div>

          <div v-if="store.isSending" class="flex justify-start">
            <div
              class="rounded-lg bg-gray-100 px-4 py-2 text-sm text-gray-500 dark:bg-gray-800 dark:text-gray-400"
            >
              Thinking&hellip;
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
