<script setup lang="ts">
defineProps<{
  project: {
    title: string
    description: string
    technologies: string[]
    githubUrl?: string
    liveUrl?: string
  }
}>()
</script>

<template>
  <article
    class="flex flex-col rounded-xl border border-gray-200 bg-white p-6 shadow-sm transition-shadow duration-200 hover:shadow-md dark:border-gray-700 dark:bg-gray-800"
  >
    <h3 class="text-lg font-semibold">
      {{ project.title }}
    </h3>
    <p class="mt-2 flex-1 text-sm leading-relaxed text-gray-500 dark:text-gray-400">
      {{ project.description }}
    </p>
    <div class="mt-4 flex flex-wrap gap-2">
      <span
        v-for="tag in project.technologies"
        :key="tag"
        class="rounded-md bg-indigo-50 px-2.5 py-0.5 text-xs font-medium text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-300"
      >
        {{ tag }}
      </span>
    </div>
    <div class="mt-6 flex gap-4">
      <a
        v-if="project.githubUrl"
        :href="project.githubUrl"
        class="text-sm font-medium underline-offset-2 hover:underline dark:text-gray-300"
        rel="noopener noreferrer"
        target="_blank"
      >
        GitHub
      </a>
      <a
        v-if="project.liveUrl"
        :href="project.liveUrl"
        class="text-sm font-medium text-indigo-600 underline-offset-2 hover:underline dark:text-indigo-400"
        rel="noopener noreferrer"
        target="_blank"
      >
        Live
      </a>
    </div>
  </article>
</template>
