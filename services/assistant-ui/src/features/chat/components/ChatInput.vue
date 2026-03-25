<script setup lang="ts">
import { ref } from 'vue'

const emit = defineEmits<{
  send: [content: string]
}>()

const input = ref('')

function handleSubmit(): void {
  const trimmed = input.value.trim()
  if (trimmed) {
    emit('send', trimmed)
    input.value = ''
  }
}
</script>

<template>
  <form class="border-t bg-white p-4" @submit.prevent="handleSubmit">
    <div class="flex gap-2">
      <input
        v-model="input"
        type="text"
        placeholder="Type a message..."
        class="flex-1 rounded border p-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
      >
      <button
        type="submit"
        class="rounded bg-blue-600 px-6 py-2 text-white hover:bg-blue-700 disabled:opacity-50"
        :disabled="!input.trim()"
      >
        Send
      </button>
    </div>
  </form>
</template>
