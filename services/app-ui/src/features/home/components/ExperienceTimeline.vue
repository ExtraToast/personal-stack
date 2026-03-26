<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t, tm } = useI18n()

interface ExperienceEntry {
  role: string
  company: string
  period: string
  description: string
  technologies: string[]
}
</script>

<template>
  <section id="experience" class="px-6 py-24">
    <div class="mx-auto max-w-3xl">
      <h2 class="font-mono text-sm font-semibold uppercase tracking-widest text-terminal-green">
        <span class="text-gray-600">02.</span> {{ t('experience.title') }}
      </h2>
      <p class="mt-3 text-gray-500">
        {{ t('experience.subtitle') }}
      </p>

      <ol class="relative mt-10 border-l-2 border-surface-border pl-8">
        <li
          v-for="(entry, index) in tm('experience.entries') as ExperienceEntry[]"
          :key="index"
          class="relative mb-12 last:mb-0"
        >
          <!-- Timeline dot -->
          <div
            class="absolute -left-[2.55rem] flex h-5 w-5 items-center justify-center rounded-full border-2 border-accent bg-surface-dark"
          >
            <div class="h-2 w-2 rounded-full bg-accent" />
          </div>

          <!-- Period badge -->
          <span
            class="inline-block rounded-md border border-surface-border bg-surface-card px-3 py-1 font-mono text-xs text-terminal-amber"
          >
            {{ entry.period }}
          </span>

          <h3 class="mt-3 text-lg font-semibold text-gray-100">
            {{ entry.role }}
          </h3>
          <p class="font-mono text-sm text-accent-light">
            {{ entry.company }}
          </p>

          <p class="mt-3 text-sm leading-relaxed text-gray-400">
            {{ entry.description }}
          </p>

          <div class="mt-4 flex flex-wrap gap-2">
            <span
              v-for="tech in entry.technologies"
              :key="tech"
              class="rounded-md border border-surface-border bg-surface-elevated px-2.5 py-0.5 font-mono text-xs text-gray-400"
            >
              {{ tech }}
            </span>
          </div>
        </li>
      </ol>
    </div>
  </section>
</template>
