<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { getSetupGuide } from '../services/projectsService'

const props = defineProps<{ projectId: string; linkId: string }>()
const markdown = ref<string | null>(null)
const error = ref<string | null>(null)

async function load(): Promise<void> {
  markdown.value = null
  error.value = null
  try {
    markdown.value = await getSetupGuide(props.projectId, props.linkId)
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'failed to load setup guide'
  }
}

onMounted(load)
watch(() => [props.projectId, props.linkId], load)
</script>

<template>
  <div data-testid="setup-guide">
    <div v-if="error" class="text-red-400 text-sm">{{ error }}</div>
    <pre
      v-else-if="markdown !== null"
      class="whitespace-pre-wrap bg-black/30 border border-gray-700 rounded p-4 text-sm font-mono leading-relaxed"
      >{{ markdown }}</pre
    >
    <div v-else class="text-gray-500 italic">Loading setup guide…</div>
  </div>
</template>
