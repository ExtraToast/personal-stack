<script setup lang="ts">
import { computed, provide, ref } from 'vue'
import { tabsInjectionKey } from './tabsContext'

interface Props {
  modelValue?: string
  /**
   * Optional `aria-label` for the tablist when no visible heading
   * names it. Strongly recommended.
   */
  ariaLabel?: string
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const known = ref<string[]>([])
const internal = ref(props.modelValue ?? '')

const active = computed<string>({
  get: () => props.modelValue ?? internal.value,
  set: (value: string) => {
    internal.value = value
    emit('update:modelValue', value)
  },
})

provide(tabsInjectionKey, {
  active,
  register(value: string) {
    if (!known.value.includes(value)) {
      known.value.push(value)
      // First registered tab wins if no v-model or no explicit
      // modelValue match — keeps the consumer's "default tab" choice
      // explicit while still being friendly when the consumer forgot.
      if (!active.value || !known.value.includes(active.value)) {
        active.value = value
      }
    }
  },
  activate(value: string) {
    if (known.value.includes(value)) active.value = value
  },
})

function onKeyDown(event: KeyboardEvent, value: string): void {
  const idx = known.value.indexOf(value)
  if (idx < 0) return
  const last = known.value.length - 1
  if (event.key === 'ArrowRight') {
    event.preventDefault()
    active.value = known.value[Math.min(idx + 1, last)] ?? value
  } else if (event.key === 'ArrowLeft') {
    event.preventDefault()
    active.value = known.value[Math.max(idx - 1, 0)] ?? value
  } else if (event.key === 'Home') {
    event.preventDefault()
    active.value = known.value[0] ?? value
  } else if (event.key === 'End') {
    event.preventDefault()
    active.value = known.value[last] ?? value
  }
}
</script>

<template>
  <div data-testid="tabs">
    <div role="tablist" :aria-label="ariaLabel" class="flex gap-1 border-b border-[var(--color-surface-border)]">
      <slot name="tabs" :active="active" :activate="(v: string) => (active = v)" :on-key-down="onKeyDown" />
    </div>
    <div class="mt-4">
      <slot />
    </div>
  </div>
</template>
