<script setup lang="ts">
import { ref } from 'vue'
import { useApiWithAuth } from '@personal-stack/vue-common'

const props = defineProps<{ workspaceId: string }>()

const open = ref(false)
const title = ref('')
const body = ref('')
const base = ref('main')
const repoDir = ref('/workspace')
const busy = ref(false)
const message = ref<string | null>(null)

async function submit(): Promise<void> {
  busy.value = true
  message.value = null
  try {
    await useApiWithAuth({ baseUrl: '/api/v1' }).post(
      `/workspaces/${props.workspaceId}/git/open-pr`,
      { repoDir: repoDir.value, title: title.value, body: body.value, base: base.value },
    )
    message.value = 'PR creation queued — watch the transcript for the gateway response.'
    open.value = false
  } catch (e: unknown) {
    message.value = `Failed: ${e instanceof Error ? e.message : 'unknown'}`
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div data-testid="open-pr-button">
    <button
      type="button"
      class="rounded bg-emerald-600 hover:bg-emerald-700 px-3 py-2 text-sm text-white"
      @click="open = !open"
    >
      {{ open ? 'Cancel PR' : 'Open PR' }}
    </button>

    <div v-if="open" class="mt-3 rounded border border-gray-700 bg-surface-darker p-4 space-y-3">
      <input
        v-model="title"
        type="text"
        placeholder="PR title"
        class="w-full rounded border border-gray-700 bg-black/30 px-3 py-2 text-sm"
      >
      <textarea
        v-model="body"
        rows="5"
        placeholder="PR body (markdown)"
        class="w-full rounded border border-gray-700 bg-black/30 px-3 py-2 text-sm font-mono"
      />
      <div class="flex gap-2">
        <input
          v-model="repoDir"
          type="text"
          placeholder="repo dir"
          class="flex-1 rounded border border-gray-700 bg-black/30 px-3 py-2 text-sm font-mono"
        >
        <input
          v-model="base"
          type="text"
          placeholder="base branch"
          class="w-32 rounded border border-gray-700 bg-black/30 px-3 py-2 text-sm"
        >
      </div>
      <button
        type="button"
        :disabled="busy"
        class="rounded bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 px-3 py-2 text-sm text-white"
        @click="submit"
      >
        {{ busy ? 'Submitting…' : 'Submit' }}
      </button>
      <p v-if="message" class="text-xs text-gray-400">{{ message }}</p>
    </div>
  </div>
</template>
