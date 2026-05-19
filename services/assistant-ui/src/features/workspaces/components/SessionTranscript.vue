<script setup lang="ts">
import type { Turn } from '../types'
import { nextTick, ref, watch } from 'vue'

const props = defineProps<{ turns: Turn[] }>()
const container = ref<HTMLDivElement | null>(null)

// Auto-scroll to bottom on new turn.
watch(
  () => props.turns.length,
  async () => {
    await nextTick()
    if (container.value) {
      container.value.scrollTop = container.value.scrollHeight
    }
  },
)
</script>

<template>
  <div
    ref="container"
    class="flex-1 overflow-y-auto bg-black/40 rounded p-4 font-mono text-sm space-y-2"
    data-testid="session-transcript"
  >
    <div
      v-for="turn in turns"
      :key="turn.id"
      class="whitespace-pre-wrap"
      :class="{
        'text-blue-300': turn.role === 'USER',
        'text-gray-100': turn.role === 'AGENT',
        'text-yellow-400 italic': turn.role === 'SYSTEM',
      }"
    >
      <span class="text-xs uppercase tracking-wider opacity-50 mr-2">{{ turn.role }}</span>
      {{ turn.body }}
    </div>
    <div v-if="turns.length === 0" class="text-gray-500 italic">
      No transcript yet — type below to start the conversation.
    </div>
  </div>
</template>
