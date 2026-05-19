<script setup lang="ts">
import type { GithubLink } from '../types'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AddLinkForm from '../components/AddLinkForm.vue'
import AttachKeyWizard from '../components/AttachKeyWizard.vue'
import { useProjectsStore } from '../stores/projects'

const route = useRoute()
const router = useRouter()
const store = useProjectsStore()

const projectId = computed(() => String(route.params.id))
const showAdd = ref(false)
const wizardLink = ref<GithubLink | null>(null)
const wizardError = ref<string | null>(null)

onMounted(async () => {
  await store.open(projectId.value)
})

async function onAdd(input: { name: string; repoUrl: string; defaultBranch: string }): Promise<void> {
  const link = await store.addNewLink(input)
  showAdd.value = false
  if (link) wizardLink.value = link
}

async function onAttach(
  link: GithubLink,
  body: { privateKeyOpenssh: string; publicKeyOpenssh: string; knownHosts: string },
): Promise<void> {
  wizardError.value = null
  try {
    await store.attach(link.id, body)
    wizardLink.value = null
  } catch (e: unknown) {
    wizardError.value = e instanceof Error ? e.message : 'attach failed'
  }
}

async function onRemove(linkId: string): Promise<void> {
  // Browser confirm() is the simplest accept/cancel surface for a
  // destructive action; a future ConfirmDialog can swap in here
  // without changing call sites.
  // eslint-disable-next-line no-alert
  if (!window.confirm('Remove this GitHub link? The Vault key will be deleted.')) return
  await store.dropLink(linkId)
}
</script>

<template>
  <div class="max-w-5xl mx-auto p-6">
    <button class="text-sm text-gray-400 hover:text-gray-200 mb-3" @click="router.push('/projects')">← Projects</button>

    <header class="mb-6">
      <h1 class="text-2xl font-bold">{{ store.activeProject?.name ?? 'Loading…' }}</h1>
      <p v-if="store.activeProject" class="text-xs text-gray-500 font-mono mt-1">
        project:{{ store.activeProject.slug }}
      </p>
      <p v-if="store.activeProject?.description" class="text-sm text-gray-400 mt-2">
        {{ store.activeProject.description }}
      </p>
    </header>

    <section class="mb-6">
      <div class="flex items-center justify-between mb-3">
        <h2 class="text-lg font-semibold">GitHub repositories</h2>
        <button
          type="button"
          class="rounded bg-blue-600 hover:bg-blue-700 px-3 py-1.5 text-sm text-white"
          @click="showAdd = !showAdd"
        >
          {{ showAdd ? 'Cancel' : 'Add repo' }}
        </button>
      </div>
      <section v-if="showAdd" class="mb-4 rounded-lg border border-gray-700 bg-surface-darker p-4">
        <AddLinkForm @submit="onAdd" @cancel="showAdd = false" />
      </section>

      <p v-if="store.links.length === 0 && !showAdd" class="text-gray-500 italic">No repositories linked yet.</p>

      <ul v-else class="space-y-3">
        <li v-for="link in store.links" :key="link.id" class="rounded-lg border border-gray-700 bg-surface-darker p-4">
          <div class="flex items-baseline justify-between mb-1">
            <div>
              <span class="font-semibold">{{ link.name }}</span>
              <span class="text-xs text-gray-500 font-mono ml-2">{{ link.repoUrl }}@{{ link.defaultBranch }}</span>
            </div>
            <div class="flex gap-2">
              <button
                v-if="!link.deployKeyFingerprint"
                class="rounded bg-emerald-600 hover:bg-emerald-700 px-3 py-1 text-xs text-white"
                @click="wizardLink = link"
              >
                Attach key
              </button>
              <button
                v-else
                class="rounded bg-emerald-600/60 hover:bg-emerald-700 px-3 py-1 text-xs text-white"
                @click="wizardLink = link"
              >
                Rotate key
              </button>
              <button class="rounded text-xs text-red-400 hover:bg-red-500/10 px-3 py-1" @click="onRemove(link.id)">
                Remove
              </button>
            </div>
          </div>
          <div v-if="link.deployKeyFingerprint" class="text-xs text-gray-400 font-mono mt-1">
            Fingerprint: {{ link.deployKeyFingerprint }}
            <span class="text-gray-500 ml-2"> attached {{ link.deployKeyAddedAt }} </span>
          </div>
          <div v-else class="text-xs text-yellow-400 mt-1">
            No deploy key attached yet — click "Attach key" to follow the setup guide.
          </div>

          <div v-if="wizardLink?.id === link.id" class="mt-4">
            <AttachKeyWizard :link="link" @submit="(v) => onAttach(link, v)" @cancel="wizardLink = null" />
            <p v-if="wizardError" class="text-red-400 text-sm mt-2">{{ wizardError }}</p>
          </div>
        </li>
      </ul>
    </section>
  </div>
</template>
