<script setup lang="ts">
import type { AttachDeployKeyInput } from '../types'
import { Modal, SubmitButton, useMutationState, useToast } from '@personal-stack/vue-common'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AttachKeyWizard from '../components/AttachKeyWizard.vue'
import { useRepositoriesStore } from '../stores/repositories'

const route = useRoute()
const router = useRouter()
const store = useRepositoriesStore()
const toast = useToast()

const id = computed(() => String(route.params.id))
const detail = computed(() => store.detailById[id.value])
const showKeyWizard = ref(false)
const destroy = useMutationState<void>()

onMounted(async () => {
  try {
    await store.loadDetail(id.value)
  } catch (e) {
    toast.errorFromCatch('Could not load the repository', e)
  }
})

// The wizard awaits this; re-throwing on failure lets the wizard's
// mutation state flip to `failure`, keeps the modal open, and lets
// the wizard render the ProblemDetail-aware toast. On success the
// wizard emits `success` and the view closes the modal below.
async function onAttachKey(input: AttachDeployKeyInput): Promise<void> {
  await store.attachKey(id.value, input)
}

async function onDestroy(): Promise<void> {
  // See ProjectView for the rationale on using window.confirm.
  // eslint-disable-next-line no-alert
  if (!window.confirm('Delete this repository? Projects linked to it lose the link; the key in Vault is removed.'))
    return
  try {
    await destroy.run(async () => {
      await store.destroy(id.value)
    })
    toast.success('Repository deleted')
    await router.push('/repositories')
  } catch (e) {
    toast.errorFromCatch('Could not delete the repository', e)
  }
}
</script>

<template>
  <div v-if="detail" class="max-w-4xl mx-auto p-6" data-testid="repository-detail">
    <header class="mb-6 flex items-baseline justify-between">
      <div>
        <h1 class="text-2xl font-bold">{{ detail.repository.name }}</h1>
        <p class="mt-1 font-mono text-xs text-[var(--color-text-muted)]">{{ detail.repository.repoUrl }}</p>
      </div>
      <SubmitButton type="button" variant="danger" label="Delete" :status="destroy.status.value" @click="onDestroy" />
    </header>

    <section class="mb-6 rounded-lg border border-[var(--color-surface-border)] bg-[var(--color-surface-card)] p-4">
      <h2 class="text-lg font-semibold">Deploy key</h2>
      <div v-if="detail.repository.deployKeyFingerprint" class="mt-2">
        <p class="text-sm text-emerald-400">Attached</p>
        <p class="mt-1 font-mono text-xs text-[var(--color-text-muted)]">
          {{ detail.repository.deployKeyFingerprint }}
        </p>
        <p class="mt-1 text-xs text-[var(--color-text-muted)]">
          since {{ new Date(detail.repository.deployKeyAddedAt ?? detail.repository.createdAt).toLocaleString() }}
        </p>
        <button
          type="button"
          class="mt-3 text-xs text-[var(--color-accent-light)] underline"
          data-testid="repository-replace-key"
          @click="showKeyWizard = true"
        >
          Replace key
        </button>
      </div>
      <div v-else class="mt-2">
        <p class="text-sm text-amber-400">No deploy key yet — the agent can't clone or push without one.</p>
        <SubmitButton
          type="button"
          label="Attach key"
          class="mt-3"
          data-testid="repository-attach-key"
          @click="showKeyWizard = true"
        />
      </div>
    </section>

    <section class="rounded-lg border border-[var(--color-surface-border)] bg-[var(--color-surface-card)] p-4">
      <h2 class="text-lg font-semibold">Linked projects</h2>
      <ul v-if="detail.attachedProjects.length > 0" class="mt-2 space-y-1">
        <li v-for="p in detail.attachedProjects" :key="p.id">
          <RouterLink
            :to="`/projects/${p.id}`"
            class="text-sm text-[var(--color-accent-light)] hover:underline"
            :data-testid="`linked-project-${p.id}`"
          >
            {{ p.name }} <span class="text-[var(--color-text-muted)] font-mono">project:{{ p.slug }}</span>
          </RouterLink>
        </li>
      </ul>
      <p v-else class="mt-2 text-sm text-[var(--color-text-muted)]">
        Not linked to any project yet. Open a project and add this repository to its pool.
      </p>
    </section>

    <Modal :open="showKeyWizard" title="Attach deploy key" @close="showKeyWizard = false">
      <AttachKeyWizard
        :repository="detail.repository"
        :on-submit="onAttachKey"
        @success="showKeyWizard = false"
        @cancel="showKeyWizard = false"
      />
    </Modal>
  </div>
  <div v-else class="max-w-4xl mx-auto p-6 text-[var(--color-text-muted)]">Loading…</div>
</template>
