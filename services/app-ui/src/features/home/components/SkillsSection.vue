<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const languages = [
  { name: 'Python', years: 6 },
  { name: 'JavaScript / TypeScript', years: 5 },
  { name: 'Java', years: 5 },
  { name: 'Ruby', years: 3 },
  { name: 'MATLAB', years: 2 },
  { name: 'Kotlin', years: 1 },
]

const frameworks = [
  { name: 'Ruby on Rails', years: 3 },
  { name: 'Angular', years: 3 },
  { name: 'Docker', years: 2 },
  { name: 'PyTorch', years: 2 },
  { name: 'Vue.js', years: 2 },
  { name: 'Spring Boot', years: 2 },
  { name: 'Nomad', years: '<1' },
]

const tools = [
  'GitHub Actions',
  'Docker Swarm',
  'JSON:API',
  'Traefik',
  'React Native',
  'PostgreSQL',
  'Vault',
]

const softSkills = ['Project Management', 'Public Relations', 'Communication', 'Critical Thinking']

const MAX_YEARS = 6

function barWidth(years: number | string): string {
  const num = typeof years === 'string' ? 0.5 : years
  return `${(num / MAX_YEARS) * 100}%`
}
</script>

<template>
  <section id="skills" class="px-6 py-24">
    <div class="mx-auto max-w-4xl">
      <h2 class="text-3xl font-bold tracking-tight">
        {{ t('skills.title') }}
      </h2>
      <p class="mt-3 text-gray-500 dark:text-gray-400">
        {{ t('skills.subtitle') }}
      </p>

      <div class="mt-10 grid gap-12 md:grid-cols-2">
        <!-- Programming languages -->
        <div>
          <h3
            class="mb-4 text-xs font-semibold uppercase tracking-widest text-indigo-600 dark:text-indigo-400"
          >
            {{ t('skills.categories.languages') }}
          </h3>
          <ul class="space-y-3">
            <li v-for="lang in languages" :key="lang.name">
              <div class="flex items-center justify-between text-sm">
                <span class="font-medium">{{ lang.name }}</span>
                <span class="text-gray-400 dark:text-gray-500"> {{ lang.years }}y </span>
              </div>
              <div class="mt-1 h-1.5 overflow-hidden rounded-full bg-gray-100 dark:bg-gray-800">
                <div
                  class="h-full rounded-full bg-indigo-500 transition-all duration-500"
                  :style="{ width: barWidth(lang.years) }"
                />
              </div>
            </li>
          </ul>
        </div>

        <!-- Frameworks -->
        <div>
          <h3
            class="mb-4 text-xs font-semibold uppercase tracking-widest text-indigo-600 dark:text-indigo-400"
          >
            {{ t('skills.categories.frameworks') }}
          </h3>
          <ul class="space-y-3">
            <li v-for="fw in frameworks" :key="fw.name">
              <div class="flex items-center justify-between text-sm">
                <span class="font-medium">{{ fw.name }}</span>
                <span class="text-gray-400 dark:text-gray-500"> {{ fw.years }}y </span>
              </div>
              <div class="mt-1 h-1.5 overflow-hidden rounded-full bg-gray-100 dark:bg-gray-800">
                <div
                  class="h-full rounded-full bg-violet-500 transition-all duration-500"
                  :style="{ width: barWidth(fw.years) }"
                />
              </div>
            </li>
          </ul>
        </div>

        <!-- Tools -->
        <div>
          <h3
            class="mb-4 text-xs font-semibold uppercase tracking-widest text-indigo-600 dark:text-indigo-400"
          >
            {{ t('skills.categories.tools') }}
          </h3>
          <div class="flex flex-wrap gap-2">
            <span
              v-for="tool in tools"
              :key="tool"
              class="rounded-md bg-sky-50 px-2.5 py-1 text-sm font-medium text-sky-700 dark:bg-sky-900/30 dark:text-sky-300"
            >
              {{ tool }}
            </span>
          </div>
        </div>

        <!-- Soft skills -->
        <div>
          <h3
            class="mb-4 text-xs font-semibold uppercase tracking-widest text-indigo-600 dark:text-indigo-400"
          >
            {{ t('skills.categories.soft') }}
          </h3>
          <div class="flex flex-wrap gap-2">
            <span
              v-for="skill in softSkills"
              :key="skill"
              class="rounded-md bg-emerald-50 px-2.5 py-1 text-sm font-medium text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-300"
            >
              {{ skill }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>
