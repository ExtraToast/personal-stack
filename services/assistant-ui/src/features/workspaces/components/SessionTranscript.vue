<script setup lang="ts">
import type { Turn } from '../types'
import { nextTick, ref, watch } from 'vue'
import BlockTurn from './BlockTurn.vue'

const props = defineProps<{ turns: Turn[] }>()
const emit = defineEmits<{
  pick: [value: { sessionId: string; optionId: string }]
  decide: [value: { sessionId: string; approved: boolean }]
  formSubmit: [value: { sessionId: string; data: Record<string, unknown> }]
}>()
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
      :class="{
        'text-blue-300': turn.role === 'USER',
        'text-[var(--color-text-primary)]': turn.role === 'AGENT',
        'text-yellow-400 italic': turn.role === 'SYSTEM',
      }"
    >
      <span class="text-xs uppercase tracking-wider opacity-50 mr-2">{{ turn.role }}</span>
      <BlockTurn
        v-if="turn.role === 'AGENT'"
        :body="turn.body"
        @pick="(id: string) => emit('pick', { sessionId: turn.sessionId, optionId: id })"
        @decide="(v: { approved: boolean }) => emit('decide', { sessionId: turn.sessionId, ...v })"
        @form-submit="(v: Record<string, unknown>) => emit('formSubmit', { sessionId: turn.sessionId, data: v })"
      />
      <span v-else class="whitespace-pre-wrap">{{ turn.body }}</span>
    </div>
    <div v-if="turns.length === 0" class="text-[var(--color-text-muted)] italic">
      No transcript yet — type below to start the conversation.
    </div>
  </div>
</template>
