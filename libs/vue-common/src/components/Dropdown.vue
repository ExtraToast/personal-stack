<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'

interface Props {
  /**
   * Accessible label for the trigger when the slot is icon-only.
   * If the trigger slot includes visible text, this can be empty.
   */
  triggerLabel?: string
  /**
   * Alignment of the popover relative to the trigger.
   */
  align?: 'left' | 'right'
}

withDefaults(defineProps<Props>(), {
  triggerLabel: '',
  align: 'right',
})

const open = ref(false)
const root = ref<HTMLElement | null>(null)

function toggle(): void {
  open.value = !open.value
}

function close(): void {
  open.value = false
}

function onDocumentClick(event: MouseEvent): void {
  if (!open.value || !root.value) return
  if (event.target instanceof Node && !root.value.contains(event.target)) {
    close()
  }
}

function onKeyDown(event: KeyboardEvent): void {
  if (event.key === 'Escape') close()
}

document.addEventListener('click', onDocumentClick)
document.addEventListener('keydown', onKeyDown)

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('keydown', onKeyDown)
})

defineExpose({ close })
</script>

<template>
  <div ref="root" class="relative inline-block" data-testid="dropdown">
    <button
      type="button"
      :aria-label="triggerLabel || undefined"
      :aria-expanded="open"
      class="inline-flex items-center"
      data-testid="dropdown-trigger"
      @click="toggle"
    >
      <slot name="trigger" :open="open" />
    </button>
    <Transition name="dropdown">
      <div
        v-if="open"
        class="absolute z-40 mt-2 min-w-[10rem] rounded-md border border-[var(--color-surface-border)] bg-[var(--color-surface-elevated)] shadow-lg"
        :class="[align === 'right' ? 'right-0' : 'left-0']"
        role="menu"
        data-testid="dropdown-menu"
      >
        <slot :close="close" />
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.dropdown-enter-active,
.dropdown-leave-active {
  transition:
    opacity 0.12s ease,
    transform 0.12s ease;
}
.dropdown-enter-from,
.dropdown-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
