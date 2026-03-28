<script setup lang="ts">
import { ALL_ROLES } from '../services/adminService'

defineProps<{
  modelValue: string
  disabled?: boolean
}>()

defineEmits<{
  'update:modelValue': [value: string]
}>()
</script>

<template>
  <select
    class="rounded border border-surface-border bg-surface-dark px-2 py-1 font-mono text-xs text-gray-300 focus:outline-none focus:ring-1 focus:ring-terminal-green disabled:opacity-50"
    :value="modelValue"
    :disabled="disabled"
    @change="$emit('update:modelValue', ($event.target as HTMLSelectElement).value)"
  >
    <option v-for="role in ALL_ROLES" :key="role" :value="role">
      {{ role }}
    </option>
  </select>
</template>
