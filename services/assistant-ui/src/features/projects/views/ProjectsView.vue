<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CreateProjectForm from '../components/CreateProjectForm.vue'
import { useProjectsStore } from '../stores/projects'

const store = useProjectsStore()
const router = useRouter()
const showCreate = ref(false)

onMounted(() => { void store.loadAll() })

async function onCreate(input: { name: string; slug: string; description: string }): Promise<void> {
  const p = await store.create(input)
  showCreate.value = false
  void router.push(`/projects/${p.id}`)
}
</script>

<template>
  <div class="max-w-5xl mx-auto p-6">
    <header class="flex items-center justify-between mb-6">
      <h1 class="text-2xl font-bold">Projects</h1>
      <button
        type="button"
        class="rounded bg-blue-600 hover:bg-blue-700 px-4 py-2 text-sm text-white"
        @click="showCreate = !showCreate"
      >{{ showCreate ? 'Cancel' : 'New project' }}</button>
    </header>

    <section v-if="showCreate" class="mb-8 rounded-lg border border-gray-700 bg-surface-darker p-4">
      <CreateProjectForm @submit="onCreate" @cancel="showCreate = false" />
    </section>

    <p v-if="store.error" class="text-red-400 mb-4">{{ store.error }}</p>

    <div v-if="store.isLoading" class="text-gray-400">Loading…</div>
    <div v-else-if="store.projects.length === 0" class="text-gray-500 italic">
      No projects yet. Create one to start linking GitHub repositories.
    </div>
    <ul v-else class="space-y-3">
      <li
        v-for="p in store.projects"
        :key="p.id"
        class="rounded-lg border border-gray-700 bg-surface-darker p-4 cursor-pointer hover:border-gray-500"
        @click="router.push(`/projects/${p.id}`)"
      >
        <div class="flex items-baseline justify-between">
          <h3 class="font-semibold">{{ p.name }}</h3>
          <span class="text-xs text-gray-500 font-mono">project:{{ p.slug }}</span>
        </div>
        <p v-if="p.description" class="text-sm text-gray-400 mt-1">{{ p.description }}</p>
      </li>
    </ul>
  </div>
</template>
