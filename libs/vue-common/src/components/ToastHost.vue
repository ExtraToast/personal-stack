<script setup lang="ts">
import { useToast } from '../composables/useToast'

const toast = useToast()

function kindClasses(kind: 'success' | 'error' | 'info'): string {
  switch (kind) {
    case 'success':
      return 'border-emerald-500 text-emerald-200'
    case 'error':
      return 'border-red-500 text-red-200'
    case 'info':
    default:
      return 'border-[var(--color-accent)] text-[var(--color-text-primary)]'
  }
}
</script>

<template>
  <Teleport to="body">
    <div
      aria-live="polite"
      class="pointer-events-none fixed right-4 top-4 z-50 flex w-full max-w-sm flex-col gap-2"
      data-testid="toast-host"
    >
      <TransitionGroup name="toast">
        <div
          v-for="t in toast.toasts"
          :key="t.id"
          class="pointer-events-auto rounded-md border bg-[var(--color-surface-elevated)] px-4 py-3 shadow-lg"
          :class="[kindClasses(t.kind)]"
          role="status"
          data-testid="toast"
          :data-kind="t.kind"
        >
          <div class="flex items-baseline justify-between gap-3">
            <p class="text-sm font-medium">{{ t.title }}</p>
            <button
              type="button"
              class="text-xs text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)]"
              aria-label="Dismiss notification"
              @click="toast.dismiss(t.id)"
            >
              ×
            </button>
          </div>
          <p v-if="t.body" class="mt-1 whitespace-pre-line text-xs text-[var(--color-text-muted)]">{{ t.body }}</p>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<style scoped>
.toast-enter-active,
.toast-leave-active {
  transition:
    opacity 0.2s ease,
    transform 0.2s ease;
}
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
