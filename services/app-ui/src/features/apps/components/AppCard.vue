<script setup lang="ts">
import type { ServiceEntry } from '../data/serviceRegistry'
import { Icon } from '@iconify/vue'

defineProps<{
  service: ServiceEntry
}>()
</script>

<template>
  <a
    class="group flex items-center gap-4 rounded-lg border border-surface-border bg-surface-elevated p-4 transition-colors hover:border-terminal-green/50"
    :href="service.url"
    target="_blank"
    rel="noopener noreferrer"
  >
    <Icon class="h-8 w-8 shrink-0 text-terminal-green" :icon="service.icon" />
    <div class="min-w-0">
      <p class="font-mono text-sm font-bold text-gray-200 group-hover:text-terminal-green">
        {{ service.label }}
      </p>
      <p class="font-mono text-xs text-gray-500">{{ service.description }}</p>
    </div>
  </a>
</template>
