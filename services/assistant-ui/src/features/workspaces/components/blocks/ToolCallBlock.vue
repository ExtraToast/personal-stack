<script setup lang="ts">
defineProps<{ name: string; args: Record<string, unknown>; result?: unknown }>()
</script>

<template>
  <details class="rounded border border-amber-500/30 bg-amber-500/5 my-2 text-xs font-mono">
    <summary class="cursor-pointer px-3 py-1.5">
      <span class="text-amber-300">↪ {{ name }}</span>
      <span class="text-[var(--color-text-muted)] ml-2">{{ Object.keys(args).join(', ') }}</span>
    </summary>
    <div class="px-3 py-2 space-y-2">
      <div>
        <div class="text-[var(--color-text-muted)] mb-1">args</div>
        <pre>{{ JSON.stringify(args, null, 2) }}</pre>
      </div>
      <div v-if="result !== undefined">
        <div class="text-[var(--color-text-muted)] mb-1">result</div>
        <pre>{{ typeof result === 'string' ? result : JSON.stringify(result, null, 2) }}</pre>
      </div>
    </div>
  </details>
</template>
