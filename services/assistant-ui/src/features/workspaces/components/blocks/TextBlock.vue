<script setup lang="ts">
defineProps<{ md: string }>()
</script>

<template>
  <div class="whitespace-pre-wrap text-[var(--color-text-primary)] font-mono text-sm">{{ md }}</div>
</template>
