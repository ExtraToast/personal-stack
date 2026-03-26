<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PortfolioCard from './PortfolioCard.vue'

const { t, tm } = useI18n()

interface ProjectEntry {
  title: string
  description: string
  technologies: string[]
  githubUrl?: string
  liveUrl?: string
}
</script>

<template>
  <section id="projects" class="bg-gray-50 px-6 py-24 dark:bg-gray-900">
    <div class="mx-auto max-w-5xl">
      <h2 class="text-3xl font-bold tracking-tight">
        {{ t('projects.title') }}
      </h2>
      <p class="mt-3 text-gray-500 dark:text-gray-400">
        {{ t('projects.subtitle') }}
      </p>
      <div class="mt-10 grid gap-6 sm:grid-cols-2">
        <PortfolioCard
          v-for="(project, i) in tm('projects.entries') as ProjectEntry[]"
          :key="i"
          :project="project"
        />
      </div>
    </div>
  </section>
</template>
