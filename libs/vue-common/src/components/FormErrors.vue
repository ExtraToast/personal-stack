<script setup lang="ts">
import type { FormErrorBanner } from '../composables/useFormErrors'

interface Props {
  /**
   * Banner content from `useFormErrors().general.value`. When `null`,
   * the component renders nothing. Pass the banner ref directly via
   * `:error="formErrors.general.value"` — no extra unwrapping needed.
   */
  error: FormErrorBanner | null
}

defineProps<Props>()
</script>

<template>
  <div
    v-if="error"
    role="alert"
    aria-live="polite"
    class="rounded-md border border-red-500/40 bg-red-500/5 px-3 py-2 text-sm text-red-200"
    data-testid="form-errors"
  >
    <p class="font-semibold text-red-100" data-testid="form-errors-title">{{ error.title }}</p>
    <p class="mt-1 text-red-100/90" data-testid="form-errors-detail">{{ error.detail }}</p>
    <p
      v-if="error.runnerStatus && typeof error.retryAfterSeconds === 'number'"
      class="mt-1 text-xs text-red-200/80"
      data-testid="form-errors-retry"
    >
      Runner is <span class="font-mono">{{ error.runnerStatus }}</span> — retry in {{ error.retryAfterSeconds }}s.
    </p>
    <p v-else-if="error.context" class="mt-1 font-mono text-xs text-red-200/70" data-testid="form-errors-context">
      {{ error.context }}
    </p>
  </div>
</template>
