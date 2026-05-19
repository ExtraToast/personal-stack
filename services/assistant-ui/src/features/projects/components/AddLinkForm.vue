<script setup lang="ts">
import { ref } from 'vue'

const emit = defineEmits<{
  submit: [value: { name: string; repoUrl: string; defaultBranch: string }]
  cancel: []
}>()

const name = ref('')
const repoUrl = ref('')
const defaultBranch = ref('main')

function onSubmit(): void {
  if (!name.value.trim() || !repoUrl.value.trim()) return
  emit('submit', {
    name: name.value.trim(),
    repoUrl: repoUrl.value.trim(),
    defaultBranch: defaultBranch.value.trim() || 'main',
  })
}
</script>

<template>
  <form class="space-y-3" data-testid="add-link-form" @submit.prevent="onSubmit">
    <div>
      <label class="block text-sm font-medium mb-1" for="link-name">Link name</label>
      <input
        id="link-name"
        v-model="name"
        type="text"
        required
        class="w-full rounded border border-gray-700 bg-surface-darker px-3 py-2"
        placeholder="personal-stack"
      >
      <p class="text-xs text-gray-500 mt-1">Used as the deploy-key file name on your laptop and in the GitHub key title.</p>
    </div>
    <div>
      <label class="block text-sm font-medium mb-1" for="link-url">Repo URL (ssh or https)</label>
      <input
        id="link-url"
        v-model="repoUrl"
        type="text"
        required
        class="w-full rounded border border-gray-700 bg-surface-darker px-3 py-2 font-mono"
        placeholder="git@github.com:owner/repo.git"
      >
    </div>
    <div>
      <label class="block text-sm font-medium mb-1" for="link-branch">Default branch</label>
      <input
        id="link-branch"
        v-model="defaultBranch"
        type="text"
        class="w-full rounded border border-gray-700 bg-surface-darker px-3 py-2 font-mono"
      >
    </div>
    <div class="flex justify-end gap-2">
      <button type="button" class="rounded px-4 py-2 text-sm text-gray-300 hover:bg-gray-800" @click="emit('cancel')">Cancel</button>
      <button type="submit" class="rounded bg-blue-600 hover:bg-blue-700 px-4 py-2 text-sm text-white">Add link</button>
    </div>
  </form>
</template>
