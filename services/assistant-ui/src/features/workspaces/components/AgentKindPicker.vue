<script setup lang="ts">
import type { AgentKind } from '../types'

const props = withDefaults(defineProps<{ modelValue: AgentKind; compact?: boolean }>(), {
  compact: false,
})
const emit = defineEmits<{ 'update:modelValue': [value: AgentKind] }>()

const options: { value: AgentKind; label: string; description: string }[] = [
  { value: 'CLAUDE', label: 'Claude Code', description: 'Anthropic Claude via the official CLI' },
  { value: 'CODEX', label: 'Codex', description: 'OpenAI Codex via the official CLI' },
  { value: 'SHELL', label: 'Shell', description: 'Plain bash — no LLM, raw exec' },
]
</script>

<template>
  <div
    class="grid gap-2"
    :class="props.compact ? 'grid-cols-1' : 'grid-cols-1 sm:grid-cols-3'"
    data-testid="agent-kind-picker"
    aria-label="Agent kind"
  >
    <button
      v-for="opt in options"
      :key="opt.value"
      type="button"
      class="rounded-md border text-left transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-accent-light)] focus-visible:ring-offset-2 focus-visible:ring-offset-[var(--color-surface-dark)]"
      :class="[
        props.compact ? 'p-2.5' : 'p-3',
        props.modelValue === opt.value
          ? 'border-[var(--color-accent-light)] bg-[var(--color-surface-elevated)] text-[var(--color-text-primary)]'
          : 'border-[var(--color-surface-border)] bg-[var(--color-surface)] text-[var(--color-text-primary)] hover:border-[var(--color-accent-light)]',
      ]"
      :aria-pressed="props.modelValue === opt.value"
      :aria-label="opt.label"
      @click="emit('update:modelValue', opt.value)"
    >
      <div class="font-semibold">{{ opt.label }}</div>
      <div class="text-xs text-[var(--color-text-muted)] mt-1">{{ opt.description }}</div>
    </button>
  </div>
</template>
