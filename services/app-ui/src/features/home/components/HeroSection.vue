<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const showScroll = ref(true)

function onScroll(): void {
  showScroll.value = window.scrollY < 100
}

onMounted(() => window.addEventListener('scroll', onScroll))
onUnmounted(() => window.removeEventListener('scroll', onScroll))
</script>

<template>
  <section
    class="relative flex min-h-screen flex-col items-center justify-center overflow-hidden px-4 pt-16 text-center sm:px-6"
  >
    <div class="grid-pattern absolute inset-0 opacity-40" />

    <div class="relative max-w-3xl">
      <div class="animate-fade-in-up mx-auto mb-6 sm:mb-8">
        <img
          alt="Joris Jonkers"
          class="mx-auto h-36 w-36 rounded-full object-cover ring-2 ring-accent/40 ring-offset-4 ring-offset-surface-dark sm:h-48 sm:w-48"
          decoding="async"
          fetchpriority="high"
          height="192"
          loading="eager"
          sizes="(min-width: 640px) 192px, 144px"
          src="/joris-192.jpg"
          srcset="/joris-192.jpg 192w, /joris-384.jpg 384w"
          width="192"
        />
      </div>

      <div class="animate-fade-in-up delay-100 opacity-0">
        <p
          class="mb-4 inline-block rounded-md border border-surface-border bg-surface-card px-3 py-1 font-mono text-xs text-terminal-green sm:mb-6 sm:px-4 sm:py-1.5 sm:text-sm"
        >
          <span class="text-gray-600">$</span>
          {{ t('hero.subtitle') }}
        </p>
      </div>

      <h1
        class="animate-fade-in-up delay-200 text-3xl font-bold tracking-tight text-gray-100 opacity-0 sm:text-5xl md:text-7xl"
      >
        {{ t('hero.name') }}
        <span class="text-gray-600">
          {{ t('hero.degree') }}
        </span>
      </h1>

      <p class="animate-fade-in-up delay-300 mt-4 text-sm leading-relaxed text-gray-400 opacity-0 sm:mt-6 sm:text-lg">
        {{ t('hero.tagline') }}
      </p>

      <div class="animate-fade-in-up delay-400 mt-6 flex flex-wrap justify-center gap-3 opacity-0 sm:mt-10 sm:gap-4">
        <a
          class="glow-accent rounded-lg bg-accent px-5 py-2.5 font-mono text-xs font-semibold text-white transition-all hover:bg-accent-light sm:px-6 sm:py-3 sm:text-sm"
          href="https://github.com/ExtraToast"
          rel="noopener noreferrer"
          target="_blank"
        >
          {{ t('hero.cta.github') }}
        </a>
        <a
          class="rounded-lg border border-surface-border px-5 py-2.5 font-mono text-xs font-semibold text-gray-300 transition-all hover:border-accent hover:text-white sm:px-6 sm:py-3 sm:text-sm"
          href="https://www.linkedin.com/in/joris-jonkers-25520293/"
          rel="noopener noreferrer"
          target="_blank"
        >
          {{ t('hero.cta.linkedin') }}
        </a>
        <a
          class="rounded-lg border border-surface-border px-5 py-2.5 font-mono text-xs font-semibold text-gray-300 transition-all hover:border-accent hover:text-white sm:px-6 sm:py-3 sm:text-sm"
          href="mailto:info@jorisjonkers.dev"
        >
          {{ t('hero.cta.email') }}
        </a>
      </div>
    </div>

    <!-- Scroll indicator: fixed to bottom of viewport, fades on scroll -->
    <div
      class="pointer-events-none fixed bottom-8 left-0 right-0 z-10 flex flex-col items-center gap-2 text-gray-600 transition-opacity duration-300"
      :class="showScroll ? 'opacity-100' : 'opacity-0'"
    >
      <span class="font-mono text-xs uppercase tracking-widest">
        {{ t('hero.scroll') }}
      </span>
      <svg class="h-5 w-5 animate-bounce" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
        <path d="M19 9l-7 7-7-7" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </div>
  </section>
</template>
