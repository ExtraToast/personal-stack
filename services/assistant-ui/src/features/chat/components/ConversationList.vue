<script setup lang="ts">
import { useConversationStore } from '@/stores/conversation'

const store = useConversationStore()

async function onNew(): Promise<void> {
  const title = `Conversation ${new Date().toLocaleString()}`
  await store.startConversation(title)
}
</script>

<template>
  <aside class="flex h-full w-64 flex-col border-r border-surface-border bg-surface-card">
    <div class="flex items-center justify-between border-b border-surface-border px-4 py-3">
      <span class="font-mono text-sm font-semibold text-gray-300"> Conversations </span>
      <!-- prettier-ignore -->
      <button
        class="rounded-md bg-accent px-3 py-1 font-mono text-xs font-medium
          text-white transition-colors hover:bg-accent-light"
        type="button"
        @click="onNew"
      >
        New
      </button>
    </div>

    <nav class="flex-1 overflow-y-auto py-2">
      <!-- prettier-ignore -->
      <p v-if="store.isLoading" class="px-4 py-2 font-mono text-sm text-gray-600">
        Loading...
      </p>
      <p v-else-if="store.conversations.length === 0" class="px-4 py-2 font-mono text-sm text-gray-600">
        No conversations yet
      </p>
      <button
        v-for="conv in store.conversations"
        :key="conv.id"
        class="w-full px-4 py-2 text-left text-sm transition-colors hover:bg-surface-elevated"
        :class="[
          store.activeConversationId === conv.id
            ? 'border-r-2 border-accent bg-surface-elevated font-medium text-gray-200'
            : 'text-gray-500',
        ]"
        type="button"
        @click="store.selectConversation(conv.id)"
      >
        <span class="block truncate">{{ conv.title }}</span>
        <span class="font-mono text-xs text-gray-600">
          {{ new Date(conv.createdAt).toLocaleDateString() }}
        </span>
      </button>
    </nav>
  </aside>
</template>
