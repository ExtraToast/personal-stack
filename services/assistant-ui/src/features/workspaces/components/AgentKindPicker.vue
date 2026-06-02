<script setup lang="ts">
import type { AgentKind } from '../types'

defineProps<{ modelValue: AgentKind }>()
const emit = defineEmits<{ 'update:modelValue': [value: AgentKind] }>()

const options: { value: AgentKind; label: string; description: string }[] = [
  { value: 'CLAUDE', label: 'Claude Code', description: 'Anthropic Claude via the official CLI' },
  { value: 'CODEX', label: 'Codex', description: 'OpenAI Codex via the official CLI' },
  { value: 'SHELL', label: 'Shell', description: 'Plain bash — no LLM, raw exec' },
]
</script>

<template>
  <div class="grid grid-cols-1 sm:grid-cols-3 gap-2" data-testid="agent-kind-picker">
    <button
      v-for="opt in options"
      :key="opt.value"
      type="button"
      class="rounded-lg border p-3 text-left transition-colors"
      :class="
        modelValue === opt.value
          ? 'border-blue-500 bg-blue-500/10'
          : 'border-[var(--color-surface-border)] hover:border-[var(--color-text-muted)]'
      "
      @click="emit('update:modelValue', opt.value)"
    >
      <div class="font-semibold">{{ opt.label }}</div>
      <div class="text-xs text-[var(--color-text-muted)] mt-1">{{ opt.description }}</div>
    </button>
  </div>
</template>
