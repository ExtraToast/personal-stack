<script setup lang="ts">
import { ref } from 'vue'

defineProps<{
  disabled?: boolean
}>()

const emit = defineEmits<{
  send: [content: string]
}>()

const input = ref('')

function handleSubmit(): void {
  const trimmed = input.value.trim()
  if (!trimmed) return
  emit('send', trimmed)
  input.value = ''
}

function onKeydown(e: KeyboardEvent): void {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSubmit()
  }
}
</script>

<template>
  <form
    class="border-t border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900"
    @submit.prevent="handleSubmit"
  >
    <div class="flex gap-2">
      <textarea
        v-model="input"
        :disabled="disabled"
        class="flex-1 resize-none rounded-md border border-gray-300 px-3 py-2 text-sm placeholder-gray-400 focus:border-gray-900 focus:outline-none focus:ring-1 focus:ring-gray-900 disabled:opacity-50 dark:border-gray-700 dark:bg-gray-800 dark:focus:border-gray-500 dark:focus:ring-gray-500"
        placeholder="Type a message…"
        rows="2"
        @keydown="onKeydown"
      />
      <button
        :disabled="!input.trim() || disabled"
        class="self-end rounded-md bg-gray-900 px-4 py-2 text-sm font-semibold text-white hover:bg-gray-800 disabled:opacity-50 dark:bg-white dark:text-gray-900 dark:hover:bg-gray-200"
        type="submit"
      >
        Send
      </button>
    </div>
  </form>
</template>
