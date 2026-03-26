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
    class="group flex flex-col rounded-xl border border-surface-border bg-surface-elevated p-6 transition-all duration-300 hover:border-accent/40 hover:shadow-lg hover:shadow-accent/5"
  >
    <!-- Terminal-style title bar -->
    <div class="mb-4 flex items-center gap-2">
      <div class="flex gap-1.5">
        <div class="h-2.5 w-2.5 rounded-full bg-red-500/60" />
        <div class="h-2.5 w-2.5 rounded-full bg-terminal-amber/60" />
        <div class="h-2.5 w-2.5 rounded-full bg-terminal-green/60" />
      </div>
      <span class="font-mono text-xs text-gray-600"
        >~/projects/{{ project.title.toLowerCase().replace(/\s+/g, '-') }}</span
      >
    </div>

    <h3 class="text-lg font-semibold text-gray-100 group-hover:text-accent-light">
      {{ project.title }}
    </h3>
    <p class="mt-2 flex-1 text-sm leading-relaxed text-gray-400">
      {{ project.description }}
    </p>
    <div class="mt-4 flex flex-wrap gap-2">
      <span
        v-for="tag in project.technologies"
        :key="tag"
        class="rounded-md border border-accent/20 bg-accent/10 px-2.5 py-0.5 font-mono text-xs text-accent-light"
      >
        {{ tag }}
      </span>
    </div>
    <div class="mt-6 flex gap-4 font-mono text-sm">
      <a
        v-if="project.githubUrl"
        :href="project.githubUrl"
        class="text-gray-400 transition-colors hover:text-terminal-green"
        rel="noopener noreferrer"
        target="_blank"
      >
        &gt; github
      </a>
      <a
        v-if="project.liveUrl"
        :href="project.liveUrl"
        class="text-gray-400 transition-colors hover:text-terminal-cyan"
        rel="noopener noreferrer"
        target="_blank"
      >
        &gt; live
      </a>
    </div>
  </article>
</template>
