<script setup lang="ts">
import type { AgentSession } from '../types'
import { computed, nextTick, ref } from 'vue'
import { useSessionLabelsStore } from '../stores/sessionLabels'

interface Props {
  sessions: AgentSession[]
  activeId: string | null
  orientation?: 'horizontal' | 'vertical'
}

const props = withDefaults(defineProps<Props>(), {
  orientation: 'horizontal',
})

const emit = defineEmits<{
  select: [id: string]
  stop: [id: string]
}>()

const labels = useSessionLabelsStore()
const isVertical = computed(() => props.orientation === 'vertical')

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

function sessionShellClasses(s: AgentSession): string[] {
  const active = props.activeId === s.id
  const base = [
    'min-w-0 flex-1 items-center gap-2 text-sm transition-colors',
    'focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-accent-light)]',
    'focus-visible:ring-offset-2 focus-visible:ring-offset-[var(--color-surface-dark)]',
  ].join(' ')

  if (isVertical.value) {
    return [
      base,
      'flex rounded-md border px-3 py-2 text-left',
      active
        ? 'border-[var(--color-accent-light)] bg-[var(--color-surface-elevated)] text-[var(--color-text-primary)]'
        : 'border-[var(--color-surface-border)] bg-[var(--color-surface)] text-[var(--color-text-muted)] hover:border-[var(--color-accent-light)] hover:text-[var(--color-text-primary)]',
    ]
  }

  return [
    base,
    'flex rounded-t-md border-b-2 px-3 py-2.5',
    active
      ? 'border-[var(--color-accent-light)] bg-[var(--color-surface-elevated)] text-[var(--color-text-primary)]'
      : 'border-transparent text-[var(--color-text-muted)] hover:bg-[var(--color-surface-card)] hover:text-[var(--color-text-primary)]',
  ]
}
</script>

<template>
  <nav :class="isVertical ? 'space-y-2' : 'min-w-0'" data-testid="session-tabs" aria-label="Agent sessions">
    <p v-if="props.sessions.length === 0" class="text-sm italic text-[var(--color-text-muted)]">No sessions yet.</p>
    <ul
      v-else
      :class="isVertical ? 'space-y-2' : 'flex gap-1 overflow-x-auto overflow-y-hidden px-1 pt-1'"
      data-testid="session-tabs-list"
    >
      <li
        v-for="s in props.sessions"
        :key="s.id"
        class="flex min-w-0 items-stretch gap-1"
        :class="isVertical ? '' : 'w-[15rem] shrink-0'"
      >
        <div v-if="editingId === s.id" :class="sessionShellClasses(s)">
          <span class="shrink-0 rounded px-2 py-0.5 text-xs font-semibold" :class="kindBadge[s.kind]">
            {{ s.kind }}
          </span>
          <input
            :ref="(el) => focusInput(el)"
            v-model="draft"
            type="text"
            :placeholder="s.id.slice(0, 8)"
            data-testid="session-tab-rename"
            class="min-w-0 flex-1 border-b border-[var(--color-accent-light)] bg-transparent font-mono text-sm focus:outline-none"
            @click.stop
            @keydown.enter.prevent="commit(s.id)"
            @keydown.esc.prevent="cancel"
            @blur="commit(s.id)"
          />
          <span
            class="shrink-0 text-xs"
            :class="s.status === 'RUNNING' ? 'text-green-400' : 'text-[var(--color-text-muted)]'"
          >
            {{ s.status }}
          </span>
        </div>
        <button
          v-else
          type="button"
          :class="sessionShellClasses(s)"
          :aria-current="props.activeId === s.id ? 'true' : undefined"
          :data-testid="`session-tab-${s.id}`"
          @click="emit('select', s.id)"
          @contextmenu.prevent="startEdit(s)"
          @dblclick.prevent="startEdit(s)"
        >
          <span class="shrink-0 rounded px-2 py-0.5 text-xs font-semibold" :class="kindBadge[s.kind]">
            {{ s.kind }}
          </span>
          <span class="min-w-0 flex-1 truncate font-mono" :title="s.id">{{ tabLabel(s) }}</span>
          <span
            class="shrink-0 text-xs"
            :class="s.status === 'RUNNING' ? 'text-green-400' : 'text-[var(--color-text-muted)]'"
          >
            {{ s.status }}
          </span>
        </button>
        <button
          v-if="s.status === 'RUNNING'"
          type="button"
          class="shrink-0 rounded-md border border-transparent px-2 text-sm text-red-400 transition-colors hover:border-red-500/40 hover:bg-red-500/10 hover:text-red-300 focus:outline-none focus-visible:ring-2 focus-visible:ring-red-400 focus-visible:ring-offset-2 focus-visible:ring-offset-[var(--color-surface-dark)]"
          :class="isVertical ? 'self-stretch' : 'my-1 py-1'"
          :aria-label="`Stop session ${tabLabel(s)}`"
          :data-testid="`session-tab-stop-${s.id}`"
          @click="emit('stop', s.id)"
        >
          ×
        </button>
      </li>
    </ul>
  </nav>
</template>
