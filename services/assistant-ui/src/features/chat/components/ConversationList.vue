<script setup lang="ts">
import { useConversationStore } from '@/stores/conversation'

const store = useConversationStore()

async function onNew(): Promise<void> {
  const title = `Conversation ${new Date().toLocaleString()}`
  await store.startConversation(title)
}
</script>

<template>
  <aside
    class="flex h-full w-64 flex-col border-r border-gray-200 bg-gray-50 dark:border-gray-800 dark:bg-gray-900"
  >
    <div
      class="flex items-center justify-between border-b border-gray-200 px-4 py-3 dark:border-gray-800"
    >
      <span class="text-sm font-semibold"> Conversations </span>
      <button
        class="rounded-md bg-gray-900 px-3 py-1 text-xs font-medium text-white hover:bg-gray-800 dark:bg-white dark:text-gray-900 dark:hover:bg-gray-200"
        type="button"
        @click="onNew"
      >
        New
      </button>
    </div>

    <nav class="flex-1 overflow-y-auto py-2">
      <p v-if="store.isLoading" class="px-4 py-2 text-sm text-gray-400">Loading&hellip;</p>
      <p v-else-if="store.conversations.length === 0" class="px-4 py-2 text-sm text-gray-400">
        No conversations yet
      </p>
      <button
        v-for="conv in store.conversations"
        :key="conv.id"
        class="w-full px-4 py-2 text-left text-sm hover:bg-gray-100 dark:hover:bg-gray-800"
        :class="[
          store.activeConversationId === conv.id
            ? 'bg-gray-200 font-medium dark:bg-gray-800'
            : 'text-gray-600 dark:text-gray-400',
        ]"
        type="button"
        @click="store.selectConversation(conv.id)"
      >
        <span class="block truncate">{{ conv.title }}</span>
        <span class="text-xs text-gray-400 dark:text-gray-500">
          {{ new Date(conv.createdAt).toLocaleDateString() }}
        </span>
      </button>
    </nav>
  </aside>
</template>
