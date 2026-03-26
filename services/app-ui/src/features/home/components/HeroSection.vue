<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <section class="flex min-h-screen flex-col items-center justify-center px-6 pt-16 text-center">
    <div class="max-w-3xl">
      <img
        alt="Joris Jonkers"
        class="mx-auto mb-8 h-32 w-32 rounded-full object-cover ring-2 ring-indigo-500/30"
        height="128"
        src="/joris.jpg"
        width="128"
      />
      <p
        class="mb-4 text-sm font-semibold uppercase tracking-widest text-indigo-600 dark:text-indigo-400"
      >
        {{ t('hero.subtitle') }}
      </p>
      <h1 class="text-5xl font-bold tracking-tight sm:text-7xl">
        {{ t('hero.name') }}
        <span class="text-gray-400 dark:text-gray-600">{{ t('hero.degree') }}</span>
      </h1>
      <p class="mt-6 text-xl leading-relaxed text-gray-500 dark:text-gray-400">
        {{ t('hero.tagline') }}
      </p>
      <div class="mt-10 flex flex-wrap justify-center gap-4">
        <a
          class="rounded-lg bg-gray-900 px-6 py-3 text-sm font-semibold text-white transition-colors hover:bg-gray-700 dark:bg-white dark:text-gray-900 dark:hover:bg-gray-200"
          href="https://github.com/ExtraToast"
          rel="noopener noreferrer"
          target="_blank"
        >
          {{ t('hero.cta.github') }}
        </a>
        <a
          class="rounded-lg border border-gray-300 px-6 py-3 text-sm font-semibold transition-colors hover:border-gray-500 dark:border-gray-700 dark:hover:border-gray-500"
          href="https://www.linkedin.com/in/joris-jonkers-25520293/"
          rel="noopener noreferrer"
          target="_blank"
        >
          {{ t('hero.cta.linkedin') }}
        </a>
        <a
          class="rounded-lg border border-gray-300 px-6 py-3 text-sm font-semibold transition-colors hover:border-gray-500 dark:border-gray-700 dark:hover:border-gray-500"
          href="mailto:info@jorisjonkers.dev"
        >
          {{ t('hero.cta.email') }}
        </a>
      </div>
    </div>
    <div
      class="absolute bottom-10 flex flex-col items-center gap-2 text-gray-400 dark:text-gray-600"
    >
      <span class="text-xs uppercase tracking-widest">
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
