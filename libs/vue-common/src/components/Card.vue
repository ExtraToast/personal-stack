<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  /**
   * When set, the card is rendered as a `<RouterLink>` to this path
   * (or `<a>` if it looks like an absolute URL). Without this, the
   * card is a plain `<article>`.
   */
  to?: string | object
  /**
   * Padding variant. `default` matches the existing inline styling
   * across the codebase; `compact` is a tighter spacing for dense lists.
   */
  padding?: 'default' | 'compact' | 'none'
  /**
   * Whether to show the hover/focus border highlight.
   *
   * - `'auto'` (default): derive from `to` — interactive if a target
   *   path is set.
   * - `true` / `false`: explicit override that ignores `to`.
   *
   * Using a string sentinel for the default instead of `undefined`
   * avoids Vue's boolean-prop coercion treating "not passed" as
   * `false`, which would short-circuit the auto-derivation.
   */
  interactive?: boolean | 'auto'
}

const props = withDefaults(defineProps<Props>(), {
  padding: 'default',
  interactive: 'auto',
})

const isInteractive = computed(() => (props.interactive === 'auto' ? props.to !== undefined : props.interactive))

const paddingClass = computed(() => (props.padding === 'none' ? '' : props.padding === 'compact' ? 'p-3' : 'p-4'))

const cardClasses = computed(() => [
  // `block` is load-bearing when the card renders as a RouterLink:
  // browsers default `<a>` to inline, so the flex header / multi-line
  // body inside the card collapses into a single inline run with the
  // padding applied per inline-box instead of around the whole card.
  // Forcing block layout keeps the card visual identical between the
  // `<article>` and RouterLink branches.
  'block rounded-lg border border-[var(--color-surface-border)] bg-[var(--color-surface-card)] text-[var(--color-text-primary)] no-underline',
  paddingClass.value,
  isInteractive.value ? 'cursor-pointer hover:border-[var(--color-accent)] transition-colors' : '',
])
</script>

<template>
  <RouterLink v-if="to" :to="to" :class="cardClasses" data-testid="card">
    <header v-if="$slots.header" class="mb-2">
      <slot name="header" />
    </header>
    <slot />
    <footer v-if="$slots.footer" class="mt-3 pt-3 border-t border-[var(--color-surface-border)]">
      <slot name="footer" />
    </footer>
  </RouterLink>
  <article v-else :class="cardClasses" data-testid="card">
    <header v-if="$slots.header" class="mb-2">
      <slot name="header" />
    </header>
    <slot />
    <footer v-if="$slots.footer" class="mt-3 pt-3 border-t border-[var(--color-surface-border)]">
      <slot name="footer" />
    </footer>
  </article>
</template>
