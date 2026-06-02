<script setup lang="ts">
import type { AgentSession } from '../types'

defineProps<{ sessions: AgentSession[]; activeId: string | null }>()
const emit = defineEmits<{
  select: [id: string]
  stop: [id: string]
}>()

const kindBadge: Record<AgentSession['kind'], string> = {
  CLAUDE: 'bg-orange-500/20 text-orange-300',
  CODEX: 'bg-emerald-500/20 text-emerald-300',
  SHELL: 'bg-gray-500/20 text-[var(--color-text-primary)]',
}
</script>

<template>
  <div class="flex gap-1 border-b border-[var(--color-surface-border)] px-2" data-testid="session-tabs">
    <button
      v-for="s in sessions"
      :key="s.id"
      type="button"
      class="flex items-center gap-2 px-3 py-2 text-sm border-b-2 transition-colors"
      :class="
        activeId === s.id
          ? 'border-blue-500 text-white'
          : 'border-transparent text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)]'
      "
      @click="emit('select', s.id)"
    >
      <span class="px-2 py-0.5 rounded text-xs font-semibold" :class="kindBadge[s.kind]">{{ s.kind }}</span>
      <span class="font-mono">{{ s.id.slice(0, 8) }}</span>
      <span class="text-xs" :class="s.status === 'RUNNING' ? 'text-green-400' : 'text-[var(--color-text-muted)]'">{{
        s.status
      }}</span>
      <button
        v-if="s.status === 'RUNNING'"
        type="button"
        class="text-red-400 hover:text-red-300 text-xs ml-1"
        @click.stop="emit('stop', s.id)"
      >
        ×
      </button>
    </button>
  </div>
</template>
