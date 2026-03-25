<script setup lang="ts">
import type { Experience } from '../types'

const experiences: Experience[] = [
  {
    id: 'senior-fse',
    role: 'Senior Full-Stack Engineer',
    company: 'Acme Software',
    period: '2024 — Present',
    description:
      'Leading development of a customer-facing SaaS platform. Architecting event-driven microservices with Kotlin and Spring Boot, and driving the migration from a legacy monolith to a modular service mesh.',
    technologies: ['Kotlin', 'Spring Boot', 'Vue 3', 'PostgreSQL', 'Kubernetes'],
  },
  {
    id: 'software-engineer',
    role: 'Software Engineer',
    company: 'TechVentures B.V.',
    period: '2021 — 2024',
    description:
      'Built and maintained backend services in Java and Kotlin for a fintech product serving 50 000+ users. Introduced automated integration testing and improved deployment pipelines with Docker and GitLab CI.',
    technologies: ['Java', 'Kotlin', 'Docker', 'PostgreSQL', 'GitLab CI'],
  },
  {
    id: 'junior-developer',
    role: 'Junior Developer',
    company: 'Digital Agency XYZ',
    period: '2018 — 2021',
    description:
      'Developed and shipped web applications for a range of clients across retail and logistics. Worked across the full stack — REST APIs in Spring Boot, Vue.js frontends, and MySQL databases.',
    technologies: ['Java', 'Spring Boot', 'Vue.js', 'MySQL'],
  },
]
</script>

<template>
  <section id="experience" class="px-6 py-24">
    <div class="mx-auto max-w-3xl">
      <h2 class="text-3xl font-bold tracking-tight text-gray-900">Experience</h2>
      <p class="mt-3 text-gray-500">Where I have worked and what I have shipped.</p>
      <ol class="mt-10 space-y-10">
        <li v-for="(entry, index) in experiences" :key="entry.id" class="relative flex gap-6">
          <!-- Timeline spine -->
          <div class="flex flex-col items-center">
            <div
              class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full border-2 border-indigo-600 bg-white text-sm font-bold text-indigo-600"
            >
              {{ experiences.length - index }}
            </div>
            <div v-if="index < experiences.length - 1" class="mt-2 w-px flex-1 bg-gray-200" />
          </div>
          <!-- Content -->
          <div class="pb-10">
            <p class="text-xs font-semibold uppercase tracking-widest text-indigo-600">
              {{ entry.period }}
            </p>
            <h3 class="mt-1 text-lg font-semibold text-gray-900">
              {{ entry.role }}
            </h3>
            <p class="text-sm font-medium text-gray-500">
              {{ entry.company }}
            </p>
            <p class="mt-3 text-sm leading-relaxed text-gray-600">
              {{ entry.description }}
            </p>
            <div class="mt-3 flex flex-wrap gap-2">
              <span
                v-for="tech in entry.technologies"
                :key="tech"
                class="rounded-md bg-gray-100 px-2.5 py-0.5 text-xs font-medium text-gray-600"
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
