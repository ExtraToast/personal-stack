<script setup lang="ts">
import { ref } from 'vue'

const emit = defineEmits<{ submit: [value: string] }>()

const text = ref('')

function onSubmit(): void {
  const value = text.value
  if (!value.trim()) return
  emit('submit', value)
  text.value = ''
}

function onKeydown(ev: KeyboardEvent): void {
  if (ev.key === 'Enter' && !ev.shiftKey) {
    ev.preventDefault()
    onSubmit()
  }
}
</script>

<template>
  <form class="flex gap-2 items-end" data-testid="session-input" @submit.prevent="onSubmit">
    <textarea
      v-model="text"
      rows="2"
      class="flex-1 rounded border border-gray-700 bg-surface-darker px-3 py-2 font-mono text-sm resize-none"
      placeholder="Type a message (Enter to send, Shift+Enter for newline)"
      @keydown="onKeydown"
    />
    <button type="submit" class="rounded bg-blue-600 hover:bg-blue-700 px-4 py-2 text-sm text-white">Send</button>
  </form>
</template>
