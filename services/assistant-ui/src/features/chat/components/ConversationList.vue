<script setup lang="ts">
import { useConversationStore } from '@/stores/conversation'

const store = useConversationStore()

async function onNew(): Promise<void> {
  const title = `Conversation ${new Date().toLocaleString()}`
  await store.startConversation(title)
}
</script>

<template>
  <aside class="flex h-full w-64 flex-col border-r bg-gray-50">
    <div class="flex items-center justify-between border-b px-4 py-3">
      <span class="text-sm font-semibold text-gray-700">Conversations</span>
      <button
        class="rounded-md bg-gray-900 px-3 py-1 text-xs font-medium text-white hover:bg-gray-800"
        type="button"
        @click="onNew"
      >
        New
      </button>
    </div>

    <nav class="flex-1 overflow-y-auto py-2">
      <p v-if="store.isLoading" class="px-4 py-2 text-sm text-gray-400">Loading…</p>
      <p v-else-if="store.conversations.length === 0" class="px-4 py-2 text-sm text-gray-400">
        No conversations yet
      </p>
      <button
        v-for="conv in store.conversations"
        :key="conv.id"
        class="w-full px-4 py-2 text-left text-sm hover:bg-gray-100"
        :class="[
          store.activeConversationId === conv.id
            ? 'bg-gray-200 font-medium text-gray-900'
            : 'text-gray-700',
        ]"
        type="button"
        @click="store.selectConversation(conv.id)"
      >
        <span class="block truncate">{{ conv.title }}</span>
        <span class="text-xs text-gray-400">{{
          new Date(conv.createdAt).toLocaleDateString()
        }}</span>
      </button>
    </nav>
  </aside>
</template>
