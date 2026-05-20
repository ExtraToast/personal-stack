<script setup lang="ts">
interface Props {
  size?: 'sm' | 'md' | 'lg'
  /**
   * Inline label announced to screen readers. Defaults to "Loading" so
   * standalone usage is accessible by default. Submit-button-internal
   * usage passes an empty string because the button already labels
   * itself.
   */
  label?: string
}

const props = withDefaults(defineProps<Props>(), {
  size: 'md',
  label: 'Loading',
})

const dimensions = {
  sm: 'h-3 w-3 border-2',
  md: 'h-4 w-4 border-2',
  lg: 'h-6 w-6 border-[3px]',
}[props.size]
</script>

<template>
  <span
    class="inline-block animate-spin rounded-full border-current border-r-transparent align-[-0.125em]"
    :class="[dimensions]"
    role="status"
    :aria-label="label || undefined"
    :aria-hidden="label ? undefined : 'true'"
    data-testid="spinner"
  />
</template>
