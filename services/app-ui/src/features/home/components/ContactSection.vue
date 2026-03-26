<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <section id="contact" class="bg-gray-50 px-6 py-24 dark:bg-gray-900">
    <div class="mx-auto max-w-2xl text-center">
      <h2 class="text-3xl font-bold tracking-tight">
        {{ t('contact.title') }}
      </h2>
      <p class="mt-4 text-gray-500 dark:text-gray-400">
        {{ t('contact.subtitle') }}
      </p>
      <a
        class="mt-8 inline-block rounded-lg bg-indigo-600 px-8 py-3 text-sm font-semibold text-white transition-colors hover:bg-indigo-500 dark:bg-indigo-500 dark:hover:bg-indigo-400"
        href="mailto:info@jorisjonkers.dev"
      >
        {{ t('contact.cta') }}
      </a>
    </div>
  </section>
</template>
