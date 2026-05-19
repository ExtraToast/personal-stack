<script setup lang="ts">
defineProps<{ md: string }>()
</script>

<template>
  <div class="whitespace-pre-wrap text-gray-100 font-mono text-sm">{{ md }}</div>
</template>
