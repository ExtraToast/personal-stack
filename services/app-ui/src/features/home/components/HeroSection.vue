<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <section
    class="relative flex min-h-screen flex-col items-center justify-center overflow-hidden px-6 pt-16 text-center"
  >
    <div class="grid-pattern absolute inset-0 opacity-40" />

    <div class="relative max-w-3xl">
      <div class="animate-fade-in-up mx-auto mb-8">
        <img
          alt="Joris Jonkers"
          class="mx-auto h-48 w-48 rounded-full object-cover ring-2 ring-accent/40 ring-offset-4 ring-offset-surface-dark"
          height="192"
          src="/joris.jpg"
          width="192"
        />
      </div>

      <div class="animate-fade-in-up delay-100 opacity-0">
        <p
          class="mb-6 inline-block rounded-md border border-surface-border bg-surface-card px-4 py-1.5 font-mono text-sm text-terminal-green"
        >
          <span class="text-gray-600">$</span> {{ t('hero.subtitle') }}
        </p>
      </div>

      <h1
        class="animate-fade-in-up delay-200 text-5xl font-bold tracking-tight text-gray-100 opacity-0 sm:text-7xl"
      >
        {{ t('hero.name') }}
        <span class="text-gray-600">{{ t('hero.degree') }}</span>
      </h1>

      <p class="animate-fade-in-up delay-300 mt-6 text-lg leading-relaxed text-gray-400 opacity-0">
        {{ t('hero.tagline') }}
      </p>

      <div class="animate-fade-in-up delay-400 mt-10 flex flex-wrap justify-center gap-4 opacity-0">
        <a
          class="glow-accent rounded-lg bg-accent px-6 py-3 font-mono text-sm font-semibold text-white transition-all hover:bg-accent-light"
          href="https://github.com/ExtraToast"
          rel="noopener noreferrer"
          target="_blank"
        >
          {{ t('hero.cta.github') }}
        </a>
        <a
          class="rounded-lg border border-surface-border px-6 py-3 font-mono text-sm font-semibold text-gray-300 transition-all hover:border-accent hover:text-white"
          href="https://www.linkedin.com/in/joris-jonkers-25520293/"
          rel="noopener noreferrer"
          target="_blank"
        >
          {{ t('hero.cta.linkedin') }}
        </a>
        <a
          class="rounded-lg border border-surface-border px-6 py-3 font-mono text-sm font-semibold text-gray-300 transition-all hover:border-accent hover:text-white"
          href="mailto:info@jorisjonkers.dev"
        >
          {{ t('hero.cta.email') }}
        </a>
      </div>
    </div>

    <div
      class="animate-fade-in-up delay-500 absolute bottom-10 flex flex-col items-center gap-2 text-gray-600 opacity-0"
    >
      <span class="font-mono text-xs uppercase tracking-widest">
        {{ t('hero.scroll') }}
      </span>
      <svg
        class="h-5 w-5 animate-bounce"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        viewBox="0 0 24 24"
      >
        <path d="M19 9l-7 7-7-7" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </div>
  </section>
</template>
