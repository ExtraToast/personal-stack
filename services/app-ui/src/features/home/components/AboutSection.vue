<script setup lang="ts">
import type { Skill } from '../types'

const skills: Skill[] = [
  { name: 'Kotlin', category: 'backend' },
  { name: 'Java', category: 'backend' },
  { name: 'Spring Boot', category: 'backend' },
  { name: 'Vue 3', category: 'frontend' },
  { name: 'TypeScript', category: 'frontend' },
  { name: 'Docker', category: 'infrastructure' },
  { name: 'Kubernetes', category: 'infrastructure' },
  { name: 'PostgreSQL', category: 'database' },
]

const categoryLabel: Record<Skill['category'], string> = {
  backend: 'Backend',
  frontend: 'Frontend',
  infrastructure: 'Infrastructure',
  database: 'Database',
}

const categoryColor: Record<Skill['category'], string> = {
  backend: 'bg-indigo-50 text-indigo-700',
  frontend: 'bg-violet-50 text-violet-700',
  infrastructure: 'bg-sky-50 text-sky-700',
  database: 'bg-emerald-50 text-emerald-700',
}

const categories = ['backend', 'frontend', 'infrastructure', 'database'] as const

function skillsByCategory(category: Skill['category']): Skill[] {
  return skills.filter((s) => s.category === category)
}
</script>

<template>
  <section id="about" class="px-6 py-24">
    <div class="mx-auto max-w-5xl">
      <div class="grid gap-16 lg:grid-cols-2">
        <!-- Bio -->
        <div>
          <h2 class="text-3xl font-bold tracking-tight text-gray-900">About me</h2>
          <div class="mt-6 space-y-4 text-gray-600">
            <p class="leading-relaxed">
              I am a full-stack developer based in the Netherlands with a focus on backend
              engineering and self-hosted infrastructure. I enjoy designing systems that are
              maintainable and observable — not just code that works today, but platforms that are
              easy to reason about tomorrow.
            </p>
            <p class="leading-relaxed">
              Outside of my day job I run my own private stack: a Docker Swarm cluster with Traefik,
              Vault, and a collection of Spring Boot and Vue services. It is part homelab, part
              production environment, and mostly an excuse to keep learning.
            </p>
            <p class="leading-relaxed">
              When I am not at a keyboard I am probably cycling, cooking, or reading about
              distributed systems.
            </p>
          </div>
        </div>
        <!-- Skills -->
        <div>
          <h2 class="text-3xl font-bold tracking-tight text-gray-900">Skills</h2>
          <div class="mt-6 space-y-6">
            <div v-for="category in categories" :key="category">
              <p class="mb-2 text-xs font-semibold uppercase tracking-widest text-gray-400">
                {{ categoryLabel[category] }}
              </p>
              <div class="flex flex-wrap gap-2">
                <span
                  v-for="skill in skillsByCategory(category)"
                  :key="skill.name"
                  class="rounded-md px-3 py-1 text-sm font-medium"
                  :class="[categoryColor[category]]"
                >
                  {{ skill.name }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>
