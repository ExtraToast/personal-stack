<script setup lang="ts">
import type { Project } from '../types'
import PortfolioCard from './PortfolioCard.vue'

const projects: Project[] = [
  {
    id: 'private-stack',
    title: 'Private Stack',
    description:
      'A self-hosted infrastructure platform running Docker Swarm with Traefik as a reverse proxy, HashiCorp Vault for secrets management, and Spring Boot microservices. Designed for reliability and reproducibility.',
    techStack: ['Kotlin', 'Vue 3', 'Docker', 'PostgreSQL', 'Traefik', 'Vault'],
    githubUrl: 'https://github.com/jorisjonkers/private-stack',
    liveUrl: 'https://jorisjonkers.dev',
  },
  {
    id: 'oss-contributions',
    title: 'Open Source Contributions',
    description:
      'Ongoing contributions to the Kotlin and Java ecosystem — including bug fixes, documentation improvements, and feature additions to libraries used in production environments.',
    techStack: ['Kotlin', 'Java', 'Spring Boot'],
    githubUrl: 'https://github.com/jorisjonkers',
  },
]
</script>

<template>
  <section id="projects" class="bg-gray-50 px-6 py-24">
    <div class="mx-auto max-w-5xl">
      <h2 class="text-3xl font-bold tracking-tight text-gray-900">Projects</h2>
      <p class="mt-3 text-gray-500">A selection of things I have built or contributed to.</p>
      <div class="mt-10 grid gap-6 sm:grid-cols-2">
        <PortfolioCard v-for="project in projects" :key="project.id" :project="project" />
      </div>
    </div>
  </section>
</template>
