<script setup lang="ts">
import { computed, useId } from 'vue'
import { useI18n } from 'vue-i18n'
import { setLocale } from '@/i18n'

const { locale } = useI18n()

// Union Jack needs two clip paths; the extras slot renders both in
// the desktop cluster and the mobile drawer, so the ids must be
// unique per instance to avoid duplicate-id collisions in the DOM.
const uid = useId()
const baseClip = computed(() => `flag-gb-base-${uid}`)
const stripeClip = computed(() => `flag-gb-stripe-${uid}`)

const targetLabel = computed(() => (locale.value === 'en' ? 'Schakel naar Nederlands' : 'Switch to English'))

function toggleLocale(): void {
  setLocale(locale.value === 'en' ? 'nl' : 'en')
}
</script>

<template>
  <button
    type="button"
    class="inline-flex h-8 w-8 items-center justify-center rounded-md text-[var(--color-text-muted)] transition-colors hover:text-[var(--color-terminal-cyan)]"
    :title="targetLabel"
    :aria-label="targetLabel"
    data-testid="nav-locale"
    @click="toggleLocale"
  >
    <!-- Current language flag; clicking switches to the other one. -->
    <svg v-if="locale === 'en'" viewBox="0 0 60 30" class="h-4 w-6 rounded-[2px]" role="img" aria-hidden="true">
      <clipPath :id="baseClip">
        <path d="M0,0 v30 h60 v-30 z" />
      </clipPath>
      <clipPath :id="stripeClip">
        <path d="M30,15 h30 v15 z v15 h-30 z h-30 v-15 z v-15 h30 z" />
      </clipPath>
      <g :clip-path="`url(#${baseClip})`">
        <path d="M0,0 v30 h60 v-30 z" fill="#012169" />
        <path d="M0,0 L60,30 M60,0 L0,30" stroke="#fff" stroke-width="6" />
        <path d="M0,0 L60,30 M60,0 L0,30" :clip-path="`url(#${stripeClip})`" stroke="#C8102E" stroke-width="4" />
        <path d="M30,0 v30 M0,15 h60" stroke="#fff" stroke-width="10" />
        <path d="M30,0 v30 M0,15 h60" stroke="#C8102E" stroke-width="6" />
      </g>
    </svg>
    <svg v-else viewBox="0 0 9 6" class="h-4 w-6 rounded-[2px]" role="img" aria-hidden="true">
      <rect width="9" height="6" fill="#21468B" />
      <rect width="9" height="4" fill="#fff" />
      <rect width="9" height="2" fill="#AE1C28" />
    </svg>
  </button>
</template>
