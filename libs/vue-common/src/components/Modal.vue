<script setup lang="ts">
import { onBeforeUnmount, onMounted, watch } from 'vue'

interface Props {
  open: boolean
  /**
   * Whether clicking the dim background closes the modal. Default true.
   * Set to false for confirmation dialogs where accidental dismissal
   * would lose work.
   */
  dismissOnBackdrop?: boolean
  /**
   * Title appears in the header and is announced via `aria-labelledby`.
   * Required for accessibility — pass `''` if the consumer truly wants
   * a title-less modal but be prepared to provide an `aria-label` on
   * the slot.
   */
  title: string
}

const props = withDefaults(defineProps<Props>(), {
  dismissOnBackdrop: true,
})

const emit = defineEmits<{
  close: []
}>()

function onKeyDown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && props.open) {
    emit('close')
  }
}

function onBackdropClick(): void {
  if (props.dismissOnBackdrop) emit('close')
}

onMounted(() => {
  window.addEventListener('keydown', onKeyDown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeyDown)
})

watch(
  () => props.open,
  (next) => {
    // Body scroll lock — small footprint, no library. Restored when
    // the modal closes or the host unmounts. `immediate: true` so the
    // lock applies when the modal is mounted already open (e.g. a
    // route that opens with the modal in `open: true` state from a
    // store).
    if (typeof document === 'undefined') return
    document.body.style.overflow = next ? 'hidden' : ''
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  if (typeof document !== 'undefined') document.body.style.overflow = ''
})
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div
        v-if="open"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"
        data-testid="modal-backdrop"
        @click.self="onBackdropClick"
      >
        <div
          class="flex max-h-[90vh] w-full max-w-lg flex-col rounded-lg border border-[var(--color-surface-border)] bg-[var(--color-surface-card)] shadow-2xl"
          role="dialog"
          aria-modal="true"
          :aria-labelledby="title ? 'modal-title' : undefined"
          data-testid="modal"
        >
          <header
            v-if="title"
            class="flex flex-none items-baseline justify-between border-b border-[var(--color-surface-border)] px-5 py-3"
          >
            <h2 id="modal-title" class="text-lg font-semibold">{{ title }}</h2>
            <button
              type="button"
              class="rounded p-1 text-[var(--color-text-muted)] hover:bg-[var(--color-surface-elevated)] hover:text-[var(--color-text-primary)]"
              aria-label="Close dialog"
              data-testid="modal-close"
              @click="emit('close')"
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                class="h-4 w-4"
              >
                <line x1="18" y1="6" x2="6" y2="18" />
                <line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </header>
          <div class="min-h-0 flex-1 overflow-y-auto p-5">
            <slot />
          </div>
          <footer
            v-if="$slots.footer"
            class="flex flex-none justify-end gap-2 border-t border-[var(--color-surface-border)] px-5 py-3"
          >
            <slot name="footer" />
          </footer>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.15s ease;
}
.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}
</style>
