<script setup lang="ts">
import type { GithubLink } from '../types'
import { ref } from 'vue'
import SetupGuide from './SetupGuide.vue'

defineProps<{ link: GithubLink }>()
const emit = defineEmits<{
  submit: [value: { privateKeyOpenssh: string; publicKeyOpenssh: string; knownHosts: string }]
  cancel: []
}>()

const privateKey = ref('')
const publicKey = ref('')
const knownHosts = ref('')
const busy = ref(false)
const error = ref<string | null>(null)

function onSubmit(): void {
  if (!privateKey.value.trim() || !publicKey.value.trim()) return
  busy.value = true
  error.value = null
  emit('submit', {
    privateKeyOpenssh: privateKey.value,
    publicKeyOpenssh: publicKey.value,
    knownHosts: knownHosts.value,
  })
  busy.value = false
}
</script>

<template>
  <div
    class="rounded-lg border border-[var(--color-surface-border)] bg-surface-darker p-4 space-y-6"
    data-testid="attach-key-wizard"
  >
    <div>
      <h3 class="text-lg font-semibold mb-3">Setup guide</h3>
      <SetupGuide :project-id="link.projectId" :link-id="link.id" />
    </div>

    <form class="space-y-3" @submit.prevent="onSubmit">
      <h3 class="text-lg font-semibold">Paste the keys</h3>
      <div>
        <label class="block text-sm font-medium mb-1">Private key</label>
        <textarea
          v-model="privateKey"
          rows="10"
          required
          class="w-full rounded border border-[var(--color-surface-border)] bg-black/40 px-3 py-2 font-mono text-xs"
          placeholder="-----BEGIN OPENSSH PRIVATE KEY-----&#10;…&#10;-----END OPENSSH PRIVATE KEY-----"
        />
      </div>
      <div>
        <label class="block text-sm font-medium mb-1">Public key</label>
        <input
          v-model="publicKey"
          type="text"
          required
          class="w-full rounded border border-[var(--color-surface-border)] bg-black/40 px-3 py-2 font-mono text-xs"
          placeholder="ssh-ed25519 AAAA… name@laptop"
        />
      </div>
      <div>
        <label class="block text-sm font-medium mb-1">known_hosts (optional)</label>
        <textarea
          v-model="knownHosts"
          rows="3"
          class="w-full rounded border border-[var(--color-surface-border)] bg-black/40 px-3 py-2 font-mono text-xs"
          placeholder="output of `ssh-keyscan github.com`"
        />
        <p class="text-xs text-[var(--color-text-muted)] mt-1">
          Leave blank to use the API's bundled GitHub host keys.
        </p>
      </div>
      <p v-if="error" class="text-red-400 text-sm">{{ error }}</p>
      <div class="flex justify-end gap-2">
        <button
          type="button"
          class="rounded px-4 py-2 text-sm text-[var(--color-text-primary)] hover:bg-[var(--color-surface-elevated)]"
          @click="emit('cancel')"
        >
          Cancel
        </button>
        <button
          type="submit"
          :disabled="busy"
          class="rounded bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 px-4 py-2 text-sm text-white"
        >
          Attach key
        </button>
      </div>
    </form>
  </div>
</template>
