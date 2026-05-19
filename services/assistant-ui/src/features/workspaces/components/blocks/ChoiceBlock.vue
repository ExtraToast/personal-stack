<script setup lang="ts">
import type { ChoiceOption } from '../../types/blocks'

defineProps<{ prompt: string; options: ChoiceOption[] }>()
const emit = defineEmits<{ pick: [id: string] }>()
</script>

<template>
  <div class="rounded-lg border border-blue-500/30 bg-blue-500/5 p-4 my-2">
    <p class="font-medium mb-3">{{ prompt }}</p>
    <div class="flex flex-wrap gap-2">
      <button
        v-for="opt in options"
        :key="opt.id"
        type="button"
        class="rounded bg-blue-600 hover:bg-blue-700 px-3 py-1.5 text-sm text-white"
        @click="emit('pick', opt.id)"
      >
        {{ opt.label }}
      </button>
    </div>
  </div>
</template>
