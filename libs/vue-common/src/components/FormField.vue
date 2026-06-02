<script setup lang="ts">
import { computed, useId } from 'vue'

interface Props {
  label: string
  /**
   * Error message displayed under the field. When set, the wrapped
   * input gets `aria-invalid="true"` and `aria-describedby` pointing
   * at the error id so screen readers announce it. `undefined`
   * is accepted explicitly so call sites can bind directly from a
   * `useFormErrors().fieldErrorFor(name)` lookup without coercing.
   */
  error?: string | undefined
  /**
   * Optional helper hint displayed under the field when there's no
   * error. The error message takes precedence when both are set.
   */
  hint?: string | undefined
  /**
   * Marks the field as required. Renders a red asterisk; doesn't
   * enforce HTML5 validation (that's on the inner input).
   */
  required?: boolean
}

const props = defineProps<Props>()

const inputId = useId()
const errorId = useId()
const hintId = useId()

const describedBy = computed(() => {
  if (props.error) return errorId
  if (props.hint) return hintId
  return undefined
})
</script>

<template>
  <div class="space-y-1" data-testid="form-field">
    <label :for="inputId" class="block text-sm font-medium text-[var(--color-text-primary)]">
      {{ label }}
      <span v-if="required" class="text-red-400" aria-label="required">*</span>
    </label>
    <slot :id="inputId" :described-by="describedBy" :invalid="!!error" />
    <p v-if="error" :id="errorId" class="text-sm text-red-400" data-testid="form-field-error">
      {{ error }}
    </p>
    <p v-else-if="hint" :id="hintId" class="text-xs text-[var(--color-text-muted)]">{{ hint }}</p>
  </div>
</template>
