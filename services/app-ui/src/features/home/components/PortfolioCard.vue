<script setup lang="ts">
import type { Project } from '../types'

defineProps<{
  project: Project
}>()
</script>

<template>
  <article
    class="flex flex-col rounded-xl border border-gray-200 bg-white p-6 shadow-sm transition-shadow duration-200 hover:shadow-md"
  >
    <h3 class="text-lg font-semibold text-gray-900">
      {{ project.title }}
    </h3>
    <p class="mt-2 flex-1 text-sm leading-relaxed text-gray-500">
      {{ project.description }}
    </p>
    <div class="mt-4 flex flex-wrap gap-2">
      <span
        v-for="tag in project.techStack"
        :key="tag"
        class="rounded-md bg-indigo-50 px-2.5 py-0.5 text-xs font-medium text-indigo-700"
      >
        {{ tag }}
      </span>
    </div>
    <div class="mt-6 flex gap-4">
      <a
        v-if="project.githubUrl"
        :href="project.githubUrl"
        target="_blank"
        rel="noopener noreferrer"
        class="text-sm font-medium text-gray-700 underline-offset-2 hover:text-gray-900 hover:underline"
      >
        GitHub
      </a>
      <a
        v-if="project.liveUrl"
        :href="project.liveUrl"
        target="_blank"
        rel="noopener noreferrer"
        class="text-sm font-medium text-indigo-600 underline-offset-2 hover:text-indigo-800 hover:underline"
      >
        Live site
      </a>
    </div>
  </article>
</template>
