<script setup lang="ts">
const links = [
  {
    label: 'GitHub',
    href: 'https://github.com/jorisjonkers',
    primary: true,
  },
  {
    label: 'LinkedIn',
    href: 'https://linkedin.com/in/jorisjonkers',
    primary: false,
  },
  {
    label: 'Email',
    href: 'mailto:joris@jorisjonkers.dev',
    primary: false,
  },
]
</script>

<template>
  <section class="flex min-h-screen flex-col items-center justify-center px-6 text-center">
    <div class="max-w-3xl">
      <p class="mb-4 text-sm font-semibold uppercase tracking-widest text-indigo-600">
        Full-Stack Developer &amp; Engineer
      </p>
      <h1 class="text-6xl font-bold tracking-tight text-gray-900 sm:text-7xl">Joris Jonkers</h1>
      <p class="mt-6 text-xl leading-relaxed text-gray-500">
        Building robust, scalable software — from self-hosted infrastructure to polished user-facing
        applications. Passionate about clean architecture, developer experience, and shipping things
        that last.
      </p>
      <div class="mt-10 flex flex-wrap justify-center gap-4">
        <a
          v-for="link in links"
          :key="link.label"
          :href="link.href"
          :target="link.href.startsWith('mailto') ? undefined : '_blank'"
          rel="noopener noreferrer"
          class="rounded-lg px-6 py-3 text-sm font-semibold transition-colors duration-200"
          :class="[
            link.primary
              ? 'bg-gray-900 text-white hover:bg-gray-700'
              : 'border border-gray-300 text-gray-700 hover:border-gray-500 hover:text-gray-900',
          ]"
        >
          {{ link.label }}
        </a>
      </div>
    </div>
    <div class="absolute bottom-10 flex flex-col items-center gap-2 text-gray-400">
      <span class="text-xs uppercase tracking-widest">Scroll to explore</span>
      <svg
        class="h-5 w-5 animate-bounce"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        viewBox="0 0 24 24"
      >
        <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7" />
      </svg>
    </div>
  </section>
</template>
