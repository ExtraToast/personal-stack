<script setup lang="ts">
defineProps<{ action: string; payload: Record<string, unknown> }>()
const emit = defineEmits<{ decide: [value: { approved: boolean }] }>()
</script>

<template>
  <div class="rounded-lg border border-yellow-500/40 bg-yellow-500/10 p-4 my-2">
    <p class="font-semibold mb-2">⚠ Approval needed</p>
    <p class="text-sm mb-2">{{ action }}</p>
    <pre class="bg-black/40 p-2 rounded text-xs font-mono overflow-x-auto mb-3">{{
      JSON.stringify(payload, null, 2)
    }}</pre>
    <div class="flex gap-2">
      <button
        type="button"
        class="rounded bg-green-600 hover:bg-green-700 px-3 py-1.5 text-sm text-white"
        @click="emit('decide', { approved: true })"
      >
        Approve
      </button>
      <button
        type="button"
        class="rounded bg-red-600 hover:bg-red-700 px-3 py-1.5 text-sm text-white"
        @click="emit('decide', { approved: false })"
      >
        Reject
      </button>
    </div>
  </div>
</template>
