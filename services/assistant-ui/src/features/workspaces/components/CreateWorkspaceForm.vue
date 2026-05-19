<script setup lang="ts">
import type { Project } from '@/features/projects'
import { onMounted, ref, watch } from 'vue'
import { getProject, listProjects } from '@/features/projects'

const emit = defineEmits<{
  submit: [value: { name: string; repoUrl: string | null; branch: string | null; githubLinkId: string | null }]
  cancel: []
}>()

interface LinkOption {
  id: string
  label: string
  repoUrl: string
  defaultBranch: string
}

const name = ref('')
const repoUrl = ref('')
const branch = ref('')
const mode = ref<'project' | 'adhoc' | 'qa'>('project')

const projects = ref<Project[]>([])
const selectedProjectId = ref<string | null>(null)
const links = ref<LinkOption[]>([])
const selectedLinkId = ref<string | null>(null)

onMounted(async () => {
  projects.value = await listProjects()
  if (projects.value.length === 0) mode.value = 'adhoc'
  else selectedProjectId.value = projects.value[0]!.id
})

watch(selectedProjectId, async (id) => {
  links.value = []
  selectedLinkId.value = null
  if (!id) return
  const detail = await getProject(id)
  links.value = detail.links
    .filter((l) => !!l.deployKeyFingerprint)
    .map((l) => ({
      id: l.id,
      label: `${l.name} (${l.repoUrl}@${l.defaultBranch})`,
      repoUrl: l.repoUrl,
      defaultBranch: l.defaultBranch,
    }))
  if (links.value.length > 0) selectedLinkId.value = links.value[0]!.id
})

function onSubmit(): void {
  if (!name.value.trim()) return
  if (mode.value === 'project') {
    if (!selectedLinkId.value) return
    emit('submit', {
      name: name.value.trim(),
      repoUrl: null,
      branch: null,
      githubLinkId: selectedLinkId.value,
    })
    return
  }
  emit('submit', {
    name: name.value.trim(),
    repoUrl: mode.value === 'adhoc' ? repoUrl.value.trim() || null : null,
    branch: mode.value === 'adhoc' ? branch.value.trim() || null : null,
    githubLinkId: null,
  })
}
</script>

<template>
  <form class="space-y-4" data-testid="create-workspace-form" @submit.prevent="onSubmit">
    <div>
      <label class="block text-sm font-medium mb-1" for="ws-name">Name</label>
      <input
        id="ws-name"
        v-model="name"
        type="text"
        required
        maxlength="80"
        class="w-full rounded border border-gray-700 bg-surface-darker px-3 py-2"
        placeholder="ps-knowledge-tweaks"
      />
    </div>

    <div>
      <div class="text-sm font-medium mb-2">Type</div>
      <div class="grid grid-cols-3 gap-2">
        <button
          type="button"
          class="rounded-lg border p-3 text-left transition-colors"
          :class="mode === 'project' ? 'border-blue-500 bg-blue-500/10' : 'border-gray-700'"
          @click="mode = 'project'"
        >
          <div class="font-semibold">Project repo</div>
          <div class="text-xs text-gray-400 mt-1">Use a per-repo deploy key from a Project</div>
        </button>
        <button
          type="button"
          class="rounded-lg border p-3 text-left transition-colors"
          :class="mode === 'adhoc' ? 'border-blue-500 bg-blue-500/10' : 'border-gray-700'"
          @click="mode = 'adhoc'"
        >
          <div class="font-semibold">Ad-hoc URL</div>
          <div class="text-xs text-gray-400 mt-1">Shared deploy key — read-only access</div>
        </button>
        <button
          type="button"
          class="rounded-lg border p-3 text-left transition-colors"
          :class="mode === 'qa' ? 'border-blue-500 bg-blue-500/10' : 'border-gray-700'"
          @click="mode = 'qa'"
        >
          <div class="font-semibold">Question agent</div>
          <div class="text-xs text-gray-400 mt-1">No repo — just chat + KB</div>
        </button>
      </div>
    </div>

    <template v-if="mode === 'project'">
      <div>
        <label class="block text-sm font-medium mb-1" for="ws-project">Project</label>
        <select
          id="ws-project"
          v-model="selectedProjectId"
          class="w-full rounded border border-gray-700 bg-surface-darker px-3 py-2"
        >
          <option v-for="p in projects" :key="p.id" :value="p.id">{{ p.name }}</option>
        </select>
      </div>
      <div>
        <label class="block text-sm font-medium mb-1" for="ws-link">Repository</label>
        <select
          id="ws-link"
          v-model="selectedLinkId"
          required
          class="w-full rounded border border-gray-700 bg-surface-darker px-3 py-2"
        >
          <option v-if="links.length === 0" disabled>No repositories with attached keys</option>
          <option v-for="l in links" :key="l.id" :value="l.id">{{ l.label }}</option>
        </select>
        <p v-if="links.length === 0" class="text-xs text-yellow-400 mt-1">
          This project has no GitHub links with a deploy key attached yet.
        </p>
      </div>
    </template>

    <template v-else-if="mode === 'adhoc'">
      <div>
        <label class="block text-sm font-medium mb-1" for="ws-repo">Repo URL</label>
        <input
          id="ws-repo"
          v-model="repoUrl"
          type="text"
          class="w-full rounded border border-gray-700 bg-surface-darker px-3 py-2"
          placeholder="git@github.com:owner/repo.git"
        />
      </div>
      <div>
        <label class="block text-sm font-medium mb-1" for="ws-branch">Branch (optional)</label>
        <input
          id="ws-branch"
          v-model="branch"
          type="text"
          class="w-full rounded border border-gray-700 bg-surface-darker px-3 py-2"
          placeholder="main"
        />
      </div>
    </template>

    <div class="flex gap-2 justify-end">
      <button type="button" class="rounded px-4 py-2 text-sm text-gray-300 hover:bg-gray-800" @click="emit('cancel')">
        Cancel
      </button>
      <button type="submit" class="rounded bg-blue-600 hover:bg-blue-700 px-4 py-2 text-sm text-white">Create</button>
    </div>
  </form>
</template>
