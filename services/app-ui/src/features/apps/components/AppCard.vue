<script setup lang="ts">
import type { ServiceEntry } from '../data/serviceRegistry'
import { computed, ref } from 'vue'

const props = defineProps<{
  service: ServiceEntry
}>()

const iconLoadFailed = ref(false)

const fallbackLabel = computed(() => props.service.label.slice(0, 1).toUpperCase())

function handleIconError(): void {
  iconLoadFailed.value = true
}
</script>

<template>
  <a
    class="group flex items-center gap-4 rounded-lg border border-surface-border bg-surface-elevated p-4 transition-colors hover:border-terminal-green/50"
    :href="service.url"
    target="_blank"
    rel="noopener noreferrer"
  >
    <img
      v-if="!iconLoadFailed"
      class="h-8 w-8 shrink-0 object-contain"
      :src="service.iconUrl"
      :alt="`${service.label} icon`"
      loading="eager"
      decoding="async"
      @error="handleIconError"
    />
    <span
      v-else
      class="flex h-8 w-8 shrink-0 items-center justify-center font-mono text-xs font-bold text-terminal-green"
    >
      {{ fallbackLabel }}
    </span>
    <div class="min-w-0">
      <p class="font-mono text-sm font-bold text-gray-200 group-hover:text-terminal-green">
        {{ service.label }}
      </p>
      <p class="font-mono text-xs text-gray-500">{{ service.description }}</p>
    </div>
  </a>
</template>
