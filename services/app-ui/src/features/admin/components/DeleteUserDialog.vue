<script setup lang="ts">
defineProps<{
  username: string
  open: boolean
}>()

defineEmits<{
  confirm: []
  cancel: []
}>()
</script>

<template>
  <div
    v-if="open"
    class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm"
    role="dialog"
    aria-modal="true"
  >
    <div class="rounded-lg border border-surface-border bg-surface-dark p-6 font-mono shadow-xl">
      <p class="mb-4 text-sm text-gray-300">
        Delete user <span class="text-terminal-green">{{ username }}</span
        >? This cannot be undone.
      </p>
      <div class="flex justify-end gap-3">
        <button
          class="rounded px-3 py-1 text-xs text-gray-400 transition-colors hover:text-gray-200"
          type="button"
          @click="$emit('cancel')"
        >
          Cancel
        </button>
        <button
          class="rounded bg-red-900/50 px-3 py-1 text-xs text-red-400 transition-colors hover:bg-red-900"
          type="button"
          @click="$emit('confirm')"
        >
          Delete
        </button>
      </div>
    </div>
  </div>
</template>
