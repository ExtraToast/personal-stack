<script setup lang="ts">
import type { AttachDeployKeyInput, Repository } from '../types'
import { FormField, SubmitButton, useMutationState, useToast } from '@personal-stack/vue-common'
import { computed, ref } from 'vue'

interface Props {
  repository: Repository
  /**
   * Awaited submit handler supplied by the parent. The wizard's
   * mutation state mirrors this promise so the SubmitButton flips to
   * `failure` on a rejected server response and the modal stays open
   * for the user to fix + retry.
   */
  onSubmit: (input: AttachDeployKeyInput) => Promise<void>
}

const props = defineProps<Props>()

const emit = defineEmits<{
  success: []
  cancel: []
}>()

const privateKey = ref('')
const publicKey = ref('')
const knownHosts = ref('')

const submit = useMutationState<void>()
const toast = useToast()

const canSubmit = computed(() => privateKey.value.trim().length > 0 && publicKey.value.trim().length > 0)

async function handleSubmit(): Promise<void> {
  if (!canSubmit.value) return
  // `exactOptionalPropertyTypes`: build the payload with `knownHosts`
  // either set or omitted entirely — never `knownHosts: undefined`.
  const payload: AttachDeployKeyInput = {
    privateKeyOpenssh: privateKey.value,
    publicKeyOpenssh: publicKey.value,
  }
  const trimmedHosts = knownHosts.value.trim()
  if (trimmedHosts) payload.knownHosts = knownHosts.value
  try {
    await submit.run(() => props.onSubmit(payload))
    toast.success('Deploy key attached', `${props.repository.name} can now clone + push.`)
    emit('success')
  } catch (e) {
    toast.errorFromCatch('Could not attach the deploy key', e)
  }
}
</script>

<template>
  <div class="space-y-6" data-testid="attach-key-wizard">
    <section class="rounded-lg border border-[var(--color-surface-border)] bg-[var(--color-surface-card)] p-4">
      <h3 class="text-lg font-semibold">Generate a key</h3>
      <p class="mt-2 text-sm text-gray-400">Run this on your laptop:</p>
      <pre
        class="mt-2 overflow-x-auto rounded border border-[var(--color-surface-border)] bg-black/40 px-3 py-2 text-xs"
      ><code>ssh-keygen -t ed25519 \
  -f ./ps-{{ props.repository.name }} \
  -C "ps-{{ props.repository.name }}@personal-stack" \
  -N ""
cat ./ps-{{ props.repository.name }}.pub      # public key
cat ./ps-{{ props.repository.name }}          # private key</code></pre>
      <p class="mt-3 text-sm text-gray-400">
        Add the <em>public</em> key as a deploy key on GitHub at
        <code class="text-[var(--color-terminal-cyan)]">{{ props.repository.repoUrl }}</code> → Settings → Deploy keys →
        Add deploy key. Tick "Allow write access" only if the agent should push.
      </p>
    </section>

    <form class="space-y-4" @submit.prevent="handleSubmit">
      <h3 class="text-lg font-semibold">Paste the keys</h3>

      <FormField label="Private key" required hint="OpenSSH format; the contents of the private key file.">
        <template #default="{ id, invalid }">
          <textarea
            :id="id"
            v-model="privateKey"
            rows="10"
            required
            :aria-invalid="invalid"
            class="w-full rounded border border-[var(--color-surface-border)] bg-black/40 px-3 py-2 font-mono text-xs"
            placeholder="-----BEGIN OPENSSH PRIVATE KEY-----&#10;…&#10;-----END OPENSSH PRIVATE KEY-----"
            data-testid="attach-key-private"
          />
        </template>
      </FormField>

      <FormField label="Public key" required hint="ssh-ed25519 AAAA… name@host.">
        <template #default="{ id, invalid }">
          <input
            :id="id"
            v-model="publicKey"
            type="text"
            required
            :aria-invalid="invalid"
            class="w-full rounded border border-[var(--color-surface-border)] bg-black/40 px-3 py-2 font-mono text-xs"
            placeholder="ssh-ed25519 AAAA… name@laptop"
            data-testid="attach-key-public"
          />
        </template>
      </FormField>

      <FormField label="known_hosts" hint="Leave blank to fall back to the API's bundled GitHub host keys.">
        <template #default="{ id }">
          <textarea
            :id="id"
            v-model="knownHosts"
            rows="3"
            class="w-full rounded border border-[var(--color-surface-border)] bg-black/40 px-3 py-2 font-mono text-xs"
            placeholder="output of ssh-keyscan github.com"
            data-testid="attach-key-known-hosts"
          />
        </template>
      </FormField>

      <div class="flex justify-end gap-2">
        <SubmitButton
          type="button"
          variant="secondary"
          label="Cancel"
          :disabled="submit.pending.value"
          @click="emit('cancel')"
        />
        <SubmitButton
          label="Attach key"
          :status="submit.status.value"
          :disabled="!canSubmit"
          data-testid="attach-key-submit"
        />
      </div>
    </form>
  </div>
</template>
