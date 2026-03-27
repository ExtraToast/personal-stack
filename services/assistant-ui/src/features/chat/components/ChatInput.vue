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
  <form class="border-t border-surface-border bg-surface-card p-4" @submit.prevent="handleSubmit">
    <div class="flex gap-2">
      <!-- prettier-ignore -->
      <textarea
        v-model="input"
        :disabled="disabled"
        class="flex-1 resize-none rounded-md border border-surface-border bg-surface-elevated
          px-3 py-2 font-mono text-sm text-gray-200 placeholder-gray-600
          focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent disabled:opacity-50"
        placeholder="Type a message..."
        rows="2"
        @keydown="onKeydown"
      />
      <!-- prettier-ignore -->
      <button
        :disabled="!input.trim() || disabled"
        class="self-end rounded-md bg-accent px-4 py-2 font-mono text-sm font-semibold
          text-white transition-colors hover:bg-accent-light disabled:opacity-50"
        type="submit"
      >
        Send
      </button>
    </div>
  </form>
</template>
