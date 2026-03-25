<script setup lang="ts">
import { ref } from 'vue'
import ChatInput from '../components/ChatInput.vue'
import type { Message } from '../types'

const messages = ref<Message[]>([])

function handleSend(content: string): void {
  const userMessage: Message = {
    id: crypto.randomUUID(),
    role: 'user',
    content,
    timestamp: new Date().toISOString(),
  }
  messages.value.push(userMessage)
  // TODO: send to assistant API and handle response
}
</script>

<template>
  <div class="flex h-screen flex-col">
    <header class="border-b bg-white px-6 py-4">
      <h1 class="text-xl font-bold">Assistant</h1>
    </header>
    <main class="flex-1 overflow-y-auto p-6">
      <div v-for="message in messages" :key="message.id" class="mb-4">
        <div
          :class="[
            'max-w-2xl rounded-lg px-4 py-2',
            message.role === 'user' ? 'ml-auto bg-blue-600 text-white' : 'bg-gray-100',
          ]"
        >
          {{ message.content }}
        </div>
      </div>
    </main>
    <ChatInput @send="handleSend" />
  </div>
</template>
