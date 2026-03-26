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
      <h2 class="text-3xl font-bold tracking-tight">
        {{ t('experience.title') }}
      </h2>
      <p class="mt-3 text-gray-500 dark:text-gray-400">
        {{ t('experience.subtitle') }}
      </p>
      <ol class="mt-10 space-y-10">
        <li
          v-for="(entry, index) in tm('experience.entries') as ExperienceEntry[]"
          :key="index"
          class="relative flex gap-6"
        >
          <div class="flex flex-col items-center">
            <div
              class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full border-2 border-indigo-600 text-sm font-bold text-indigo-600 dark:border-indigo-400 dark:text-indigo-400"
            >
              {{ (tm('experience.entries') as ExperienceEntry[]).length - index }}
            </div>
            <div
              v-if="index < (tm('experience.entries') as ExperienceEntry[]).length - 1"
              class="mt-2 w-px flex-1 bg-gray-200 dark:bg-gray-700"
            />
          </div>
          <div class="pb-10">
            <p
              class="text-xs font-semibold uppercase tracking-widest text-indigo-600 dark:text-indigo-400"
            >
              {{ entry.period }}
            </p>
            <h3 class="mt-1 text-lg font-semibold">
              {{ entry.role }}
            </h3>
            <p class="text-sm font-medium text-gray-500 dark:text-gray-400">
              {{ entry.company }}
            </p>
            <p class="mt-3 text-sm leading-relaxed text-gray-600 dark:text-gray-300">
              {{ entry.description }}
            </p>
            <div class="mt-3 flex flex-wrap gap-2">
              <span
                v-for="tech in entry.technologies"
                :key="tech"
                class="rounded-md bg-gray-100 px-2.5 py-0.5 text-xs font-medium text-gray-600 dark:bg-gray-800 dark:text-gray-300"
              >
                {{ tech }}
              </span>
            </div>
          </div>
        </li>
      </ol>
    </div>
  </section>
</template>
