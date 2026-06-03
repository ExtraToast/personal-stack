<script setup lang="ts">
import type { AgentSession } from '../types'
import { nextTick, ref } from 'vue'
import { useSessionLabelsStore } from '../stores/sessionLabels'

defineProps<{ sessions: AgentSession[]; activeId: string | null }>()
const emit = defineEmits<{
  select: [id: string]
  stop: [id: string]
}>()

const labels = useSessionLabelsStore()

const kindBadge: Record<AgentSession['kind'], string> = {
  CLAUDE: 'bg-orange-500/20 text-orange-300',
  CODEX: 'bg-emerald-500/20 text-emerald-300',
  SHELL: 'bg-gray-500/20 text-[var(--color-text-primary)]',
}

function tabLabel(s: AgentSession): string {
  return labels.labelFor(s.id) ?? s.id.slice(0, 8)
}

// Right-click (or double-click) a tab to rename it inline. The default
// label is left as the input's placeholder rather than its value so a
// fresh name can be typed without first clearing the id; an empty
// commit clears any custom label and reverts to the default.
const editingId = ref<string | null>(null)
const draft = ref('')

async function startEdit(s: AgentSession): Promise<void> {
  editingId.value = s.id
  draft.value = labels.labelFor(s.id) ?? ''
  await nextTick()
}

function commit(id: string): void {
  if (editingId.value !== id) return
  labels.rename(id, draft.value)
  editingId.value = null
}

function cancel(): void {
  editingId.value = null
}

function focusInput(el: unknown): void {
  if (el instanceof HTMLInputElement) {
    el.focus()
    el.select()
  }
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
      @contextmenu.prevent="startEdit(s)"
      @dblclick.prevent="startEdit(s)"
    >
      <span class="px-2 py-0.5 rounded text-xs font-semibold" :class="kindBadge[s.kind]">{{ s.kind }}</span>
      <input
        v-if="editingId === s.id"
        :ref="(el) => focusInput(el)"
        v-model="draft"
        type="text"
        :placeholder="s.id.slice(0, 8)"
        data-testid="session-tab-rename"
        class="w-28 bg-transparent border-b border-blue-500 font-mono text-sm focus:outline-none"
        @click.stop
        @keydown.enter.prevent="commit(s.id)"
        @keydown.esc.prevent="cancel"
        @blur="commit(s.id)"
      />
      <span v-else class="font-mono" :title="s.id">{{ tabLabel(s) }}</span>
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
