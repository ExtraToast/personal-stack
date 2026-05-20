<script setup lang="ts">
import type { MutationStatus } from '../composables/useMutationState'
import { computed } from 'vue'
import Spinner from './Spinner.vue'

interface Props {
  label: string
  /**
   * Visual treatment. `primary` for the dominant submit on a form,
   * `secondary` for muted actions ("Cancel"), `danger` for destructive
   * confirms ("Delete"), `ghost` for low-emphasis inline triggers.
   */
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost'
  /**
   * Wired to a `useMutationState` instance — the button reflects its
   * status directly. When provided, the consumer doesn't need to pass
   * `disabled`/`loading` separately.
   */
  status?: MutationStatus
  /**
   * Manual override for the disabled state. Independent of `status` —
   * useful when the form has additional validity gates beyond "is a
   * submit in flight". The button is disabled when EITHER this is
   * true OR `status === 'pending'`.
   */
  disabled?: boolean
  /**
   * Optional override for the button's `type`. Defaults to `submit`
   * since the most common host is a `<form>`. Set to `button` if the
   * caller is wiring `@click` directly.
   */
  type?: 'submit' | 'button' | 'reset'
  /**
   * Pending-state label override. Defaults to "Submitting…". The
   * idle/success/failure label always comes from `label`.
   */
  pendingLabel?: string
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'primary',
  status: 'idle',
  disabled: false,
  type: 'submit',
  pendingLabel: 'Submitting…',
})

const emit = defineEmits<{
  click: [event: MouseEvent]
}>()

const isPending = computed(() => props.status === 'pending')
const isSuccess = computed(() => props.status === 'success')
const isFailure = computed(() => props.status === 'failure')
const isBlocked = computed(() => props.disabled || isPending.value)

const variantClasses = computed(() => {
  switch (props.variant) {
    case 'primary':
      return 'bg-[var(--color-accent)] hover:bg-[var(--color-accent-light)] text-white border border-transparent shadow-sm'
    case 'secondary':
      return 'bg-[var(--color-surface-elevated)] hover:bg-[var(--color-surface-border)] text-[var(--color-text-primary)] border border-[var(--color-surface-border)]'
    case 'danger':
      return 'bg-red-600 hover:bg-red-700 text-white border border-transparent shadow-sm'
    case 'ghost':
      return 'bg-transparent hover:bg-[var(--color-surface-elevated)] text-[var(--color-text-muted)] border border-transparent'
  }
  return ''
})

function handleClick(event: MouseEvent): void {
  // Pending and disabled clicks are swallowed defensively even though
  // the `disabled` attribute on the host button should already prevent
  // them — older browsers + some test harnesses still fire click on
  // disabled buttons.
  if (isBlocked.value) {
    event.preventDefault()
    event.stopPropagation()
    return
  }
  emit('click', event)
}
</script>

<template>
  <button
    :type="type"
    :disabled="isBlocked"
    :aria-busy="isPending"
    class="inline-flex items-center justify-center gap-2 rounded-md px-4 py-2 text-sm font-medium transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-accent-light)] focus-visible:ring-offset-2 focus-visible:ring-offset-[var(--color-surface-dark)] disabled:opacity-60 disabled:cursor-not-allowed"
    :class="[variantClasses]"
    data-testid="submit-button"
    :data-status="status"
    @click="handleClick"
  >
    <template v-if="isPending">
      <Spinner size="sm" :label="pendingLabel" />
      <span>{{ pendingLabel }}</span>
    </template>
    <template v-else-if="isSuccess">
      <svg
        xmlns="http://www.w3.org/2000/svg"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="3"
        stroke-linecap="round"
        stroke-linejoin="round"
        class="h-4 w-4"
        aria-hidden="true"
      >
        <polyline points="20 6 9 17 4 12" />
      </svg>
      <span>{{ label }}</span>
    </template>
    <template v-else-if="isFailure">
      <svg
        xmlns="http://www.w3.org/2000/svg"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="3"
        stroke-linecap="round"
        stroke-linejoin="round"
        class="h-4 w-4"
        aria-hidden="true"
      >
        <line x1="18" y1="6" x2="6" y2="18" />
        <line x1="6" y1="6" x2="18" y2="18" />
      </svg>
      <span>{{ label }}</span>
    </template>
    <template v-else>
      <span>{{ label }}</span>
    </template>
  </button>
</template>
