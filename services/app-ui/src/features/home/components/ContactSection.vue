<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <section id="contact" class="border-t border-surface-border/40 bg-surface-card px-6 py-24">
    <div class="mx-auto max-w-2xl text-center">
      <h2 class="font-mono text-sm font-semibold uppercase tracking-widest text-terminal-green">
        <span class="text-gray-600">05.</span> {{ t('contact.title') }}
      </h2>
      <p class="mt-6 text-lg text-gray-400">
        {{ t('contact.subtitle') }}
      </p>
      <a
        class="glow-accent mt-8 inline-block rounded-lg bg-accent px-8 py-3 font-mono text-sm font-semibold text-white transition-all hover:bg-accent-light"
        href="mailto:info@jorisjonkers.dev"
      >
        {{ t('contact.cta') }}
      </a>
    </div>
  </section>
</template>
