<script setup lang="ts">
defineProps<{ path: string; patch: string }>()
</script>

<template>
  <div class="rounded-lg border border-[var(--color-surface-border)] my-2 overflow-hidden">
    <div class="bg-[var(--color-surface-elevated)] px-3 py-1.5 text-xs font-mono">{{ path }}</div>
    <pre class="bg-black/60 p-3 text-xs font-mono overflow-x-auto"><code>{{ patch }}</code></pre>
  </div>
</template>
