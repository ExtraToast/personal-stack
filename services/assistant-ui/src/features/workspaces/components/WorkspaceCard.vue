<script setup lang="ts">
import type { Workspace } from '../types'

defineProps<{ workspace: Workspace }>()
const emit = defineEmits<{ open: [id: string]; destroy: [id: string] }>()

const statusColor: Record<Workspace['status'], string> = {
  PENDING: 'bg-gray-500',
  STARTING: 'bg-yellow-500',
  READY: 'bg-green-500',
  IDLE: 'bg-blue-500',
  FAILED: 'bg-red-500',
  DESTROYED: 'bg-gray-700',
}
</script>

<template>
  <div
    class="rounded-lg border border-gray-700 bg-surface-darker p-4 hover:border-gray-500 transition-colors"
    data-testid="workspace-card"
  >
    <div class="flex items-center justify-between mb-2">
      <h3 class="font-semibold">{{ workspace.name }}</h3>
      <span
        class="inline-flex items-center gap-1 text-xs"
        :class="['text-gray-300']"
      >
        <span class="h-2 w-2 rounded-full" :class="statusColor[workspace.status]" />
        {{ workspace.status }}
      </span>
    </div>
    <div v-if="workspace.repoUrl" class="text-xs text-gray-400 mb-3 font-mono break-all">
      {{ workspace.repoUrl }}<template v-if="workspace.branch">@{{ workspace.branch }}</template>
    </div>
    <div v-else class="text-xs text-gray-500 italic mb-3">Q&A workspace (no repo)</div>
    <div class="flex gap-2 justify-end">
      <button
        type="button"
        class="rounded px-3 py-1 text-xs text-red-400 hover:bg-red-500/10"
        @click="emit('destroy', workspace.id)"
      >
        Destroy
      </button>
      <button
        type="button"
        class="rounded bg-blue-600 hover:bg-blue-700 px-3 py-1 text-xs text-white"
        @click="emit('open', workspace.id)"
      >
        Open
      </button>
    </div>
  </div>
</template>
