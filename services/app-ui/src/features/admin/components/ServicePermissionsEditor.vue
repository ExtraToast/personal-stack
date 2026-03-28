<script setup lang="ts">
import { ALL_SERVICES } from '../services/adminService'

defineProps<{
  modelValue: string[]
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string[]]
}>()

function toggle(service: string, current: string[]): void {
  const next = current.includes(service) ? current.filter((s) => s !== service) : [...current, service]
  emit('update:modelValue', next)
}
</script>

<template>
  <div class="flex flex-wrap gap-1">
    <button
      v-for="service in ALL_SERVICES"
      :key="service"
      class="rounded px-2 py-0.5 font-mono text-xs transition-colors disabled:opacity-50"
      :class="
        modelValue.includes(service)
          ? 'bg-terminal-green/20 text-terminal-green'
          : 'bg-surface-elevated text-gray-500 hover:text-gray-300'
      "
      type="button"
      :disabled="disabled"
      @click="toggle(service, modelValue)"
    >
      {{ service }}
    </button>
  </div>
</template>
