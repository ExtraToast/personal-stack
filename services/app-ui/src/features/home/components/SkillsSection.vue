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

const tools = ['GitHub Actions', 'Docker Swarm', 'JSON:API', 'Traefik', 'React Native', 'PostgreSQL', 'Vault']

const softSkills = ['Project Management', 'Public Relations', 'Communication', 'Critical Thinking']

const MAX_YEARS = 6

function barWidth(years: number | string): string {
  const num = typeof years === 'string' ? 0.5 : years
  return `${(num / MAX_YEARS) * 100}%`
}
</script>

<template>
  <section id="skills" class="px-4 py-12 sm:px-6 sm:py-24">
    <div class="mx-auto max-w-4xl">
      <h2 class="font-mono text-sm font-semibold uppercase tracking-widest text-terminal-green">
        <span class="text-gray-600">04.</span>
        {{ t('skills.title') }}
      </h2>
      <p class="mt-3 text-gray-500">
        {{ t('skills.subtitle') }}
      </p>

      <div class="mt-6 grid gap-8 sm:mt-10 sm:gap-12 md:grid-cols-2">
        <!-- Programming languages -->
        <div>
          <h3 class="mb-4 font-mono text-xs font-semibold uppercase tracking-widest text-terminal-cyan">
            {{ t('skills.categories.languages') }}
          </h3>
          <ul class="space-y-3">
            <li v-for="lang in languages" :key="lang.name">
              <div class="flex items-center justify-between font-mono text-sm">
                <span class="text-gray-300">{{ lang.name }}</span>
                <span class="text-gray-600">{{ lang.years }}y</span>
              </div>
              <div class="mt-1.5 h-1.5 overflow-hidden rounded-full bg-surface-elevated">
                <div
                  class="h-full rounded-full bg-gradient-to-r from-accent to-terminal-cyan transition-all duration-500"
                  :style="{ width: barWidth(lang.years) }"
                />
              </div>
            </li>
          </ul>
        </div>

        <!-- Frameworks -->
        <div>
          <h3 class="mb-4 font-mono text-xs font-semibold uppercase tracking-widest text-terminal-purple">
            {{ t('skills.categories.frameworks') }}
          </h3>
          <ul class="space-y-3">
            <li v-for="fw in frameworks" :key="fw.name">
              <div class="flex items-center justify-between font-mono text-sm">
                <span class="text-gray-300">{{ fw.name }}</span>
                <span class="text-gray-600">{{ fw.years }}y</span>
              </div>
              <div class="mt-1.5 h-1.5 overflow-hidden rounded-full bg-surface-elevated">
                <div
                  class="h-full rounded-full bg-gradient-to-r from-terminal-purple to-terminal-pink transition-all duration-500"
                  :style="{ width: barWidth(fw.years) }"
                />
              </div>
            </li>
          </ul>
        </div>

        <!-- Tools -->
        <div>
          <h3 class="mb-4 font-mono text-xs font-semibold uppercase tracking-widest text-terminal-amber">
            {{ t('skills.categories.tools') }}
          </h3>
          <div class="flex flex-wrap gap-2">
            <span
              v-for="tool in tools"
              :key="tool"
              class="rounded-md border border-terminal-cyan/20 bg-terminal-cyan/10 px-3 py-1.5 font-mono text-sm text-terminal-cyan"
            >
              {{ tool }}
            </span>
          </div>
        </div>

        <!-- Soft skills -->
        <div>
          <h3 class="mb-4 font-mono text-xs font-semibold uppercase tracking-widest text-terminal-green">
            {{ t('skills.categories.soft') }}
          </h3>
          <div class="flex flex-wrap gap-2">
            <span
              v-for="skill in softSkills"
              :key="skill"
              class="rounded-md border border-terminal-green/20 bg-terminal-green/10 px-3 py-1.5 font-mono text-sm text-terminal-green"
            >
              {{ skill }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>
