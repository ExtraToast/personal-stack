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
  // Rotating an existing key vs attaching the first one. Only changes
  // copy + the submit-success toast; the backend overwrite-on-reattach
  // is the same write path either way.
  replacing?: boolean
}

const props = withDefaults(defineProps<Props>(), { replacing: false })

const emit = defineEmits<{
  success: []
  cancel: []
}>()

const privateKey = ref('')
const publicKey = ref('')
const knownHosts = ref('')

// Longer reset window than the default 2 s — an attach-key failure
// is typically a Vault / RBAC issue worth surfacing to the user
// for several seconds; the default 2 s flickered past and made the
// red SubmitButton state easy to miss (and racy to assert in tests).
const submit = useMutationState<void>({ resetDelayMs: 8_000 })
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
    if (props.replacing)
      toast.success('Deploy key replaced', `${props.repository.name}'s key was rotated; verifying access.`)
    else toast.success('Deploy key attached', `${props.repository.name} can now clone + push.`)
    emit('success')
  } catch (e) {
    toast.errorFromCatch('Could not attach the deploy key', e)
  }
}
</script>

<template>
  <div class="space-y-6" data-testid="attach-key-wizard">
    <p
      v-if="props.replacing"
      class="rounded-lg border border-amber-500/30 bg-amber-500/10 px-3 py-2 text-sm text-amber-300"
      data-testid="attach-key-replace-notice"
    >
      Replacing the deploy key rotates it: the new key overwrites the one stored in Vault and the old key stops working
      once this completes.
    </p>

    <section
      class="rounded-lg border border-[var(--color-surface-border)] bg-[var(--color-surface-card)] p-4"
      data-testid="attach-key-protection-notice"
    >
      <h3 class="text-lg font-semibold">Before adding the key on GitHub</h3>
      <ul class="mt-2 list-disc space-y-1 pl-5 text-sm text-[var(--color-text-muted)]">
        <li>
          Enable <strong>branch protection</strong> on the default branch (<code
            class="text-[var(--color-terminal-cyan)]"
            >{{ props.repository.defaultBranch }}</code
          >) and block both force-push and deletion, so a misbehaving agent cannot rewrite or destroy history.
        </li>
        <li>
          Tick <strong>"Allow write access"</strong> on the GitHub deploy key only if the agent should push; leave it
          off for read-only clones.
        </li>
      </ul>
    </section>

    <section class="rounded-lg border border-[var(--color-surface-border)] bg-[var(--color-surface-card)] p-4">
      <h3 class="text-lg font-semibold">Generate a key</h3>
      <p class="mt-2 text-sm text-[var(--color-text-muted)]">Run this on your laptop:</p>
      <pre
        class="mt-2 overflow-x-auto rounded border border-[var(--color-surface-border)] bg-black/40 px-3 py-2 text-xs"
      ><code>ssh-keygen -t ed25519 \
  -f ./ps-{{ props.repository.name }} \
  -C "ps-{{ props.repository.name }}@personal-stack" \
  -N ""
cat ./ps-{{ props.repository.name }}.pub      # public key
cat ./ps-{{ props.repository.name }}          # private key</code></pre>
      <p class="mt-3 text-sm text-[var(--color-text-muted)]">
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
