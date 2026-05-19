<script setup lang="ts">
import { ref } from 'vue'

const emit = defineEmits<{
  submit: [value: { name: string; repoUrl: string | null; branch: string | null }]
  cancel: []
}>()

const name = ref('')
const repoUrl = ref('')
const branch = ref('')
const mode = ref<'repo' | 'qa'>('repo')

function onSubmit(): void {
  if (!name.value.trim()) return
  emit('submit', {
    name: name.value.trim(),
    repoUrl: mode.value === 'repo' ? (repoUrl.value.trim() || null) : null,
    branch: mode.value === 'repo' ? (branch.value.trim() || null) : null,
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
      >
    </div>

    <div>
      <div class="text-sm font-medium mb-2">Type</div>
      <div class="grid grid-cols-2 gap-2">
        <button
          type="button"
          class="rounded-lg border p-3 text-left transition-colors"
          :class="mode === 'repo' ? 'border-blue-500 bg-blue-500/10' : 'border-gray-700'"
          @click="mode = 'repo'"
        >
          <div class="font-semibold">GitHub repo</div>
          <div class="text-xs text-gray-400 mt-1">Clone, edit, push, open PR</div>
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

    <template v-if="mode === 'repo'">
      <div>
        <label class="block text-sm font-medium mb-1" for="ws-repo">Repo URL</label>
        <input
          id="ws-repo"
          v-model="repoUrl"
          type="text"
          class="w-full rounded border border-gray-700 bg-surface-darker px-3 py-2"
          placeholder="git@github.com:owner/repo.git"
        >
      </div>
      <div>
        <label class="block text-sm font-medium mb-1" for="ws-branch">Branch (optional)</label>
        <input
          id="ws-branch"
          v-model="branch"
          type="text"
          class="w-full rounded border border-gray-700 bg-surface-darker px-3 py-2"
          placeholder="main"
        >
      </div>
    </template>

    <div class="flex gap-2 justify-end">
      <button
        type="button"
        class="rounded px-4 py-2 text-sm text-gray-300 hover:bg-gray-800"
        @click="emit('cancel')"
      >
        Cancel
      </button>
      <button
        type="submit"
        class="rounded bg-blue-600 hover:bg-blue-700 px-4 py-2 text-sm text-white"
      >
        Create
      </button>
    </div>
  </form>
</template>
