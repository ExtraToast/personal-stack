<script setup lang="ts">
import { Card, Modal, SubmitButton, useMutationState, useToast } from '@personal-stack/vue-common'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ProjectRepositoryPicker from '../components/ProjectRepositoryPicker.vue'
import { useProjectsStore } from '../stores/projects'

const route = useRoute()
const router = useRouter()
const store = useProjectsStore()
const toast = useToast()

const projectId = computed(() => String(route.params.id))
const showPicker = ref(false)
const unlink = useMutationState<void>()

onMounted(async () => {
  try {
    await store.open(projectId.value)
  } catch (e) {
    toast.errorFromCatch('Could not load the project', e)
  }
})

async function onPickRepo(repositoryId: string): Promise<void> {
  try {
    await store.linkRepo(repositoryId)
    showPicker.value = false
    toast.success('Repository linked')
  } catch (e) {
    toast.errorFromCatch('Could not link the repository', e)
  }
}

async function onUnlink(repositoryId: string, repositoryName: string): Promise<void> {
  // Browser confirm() is the simplest accept/cancel surface for a
  // destructive action; a future ConfirmDialog can swap in here
  // without changing call sites.
  // eslint-disable-next-line no-alert
  if (!window.confirm(`Unlink ${repositoryName} from this project? The repository itself stays.`)) return
  try {
    await unlink.run(async () => {
      await store.unlinkRepo(repositoryId)
    })
    toast.success(`Unlinked ${repositoryName}`)
  } catch (e) {
    toast.errorFromCatch('Could not unlink the repository', e)
  }
}
</script>

<template>
  <div class="max-w-5xl mx-auto p-6">
    <button
      class="mb-3 text-sm text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)]"
      @click="router.push('/projects')"
    >
      ← Projects
    </button>

    <header class="mb-6">
      <h1 class="text-2xl font-bold">{{ store.activeProject?.name ?? 'Loading…' }}</h1>
      <p v-if="store.activeProject" class="mt-1 font-mono text-xs text-[var(--color-text-muted)]">
        project:{{ store.activeProject.slug }}
      </p>
      <p v-if="store.activeProject?.description" class="mt-2 text-sm text-[var(--color-text-muted)]">
        {{ store.activeProject.description }}
      </p>
    </header>

    <section class="mb-6">
      <div class="mb-3 flex items-center justify-between">
        <div>
          <h2 class="text-lg font-semibold">Repositories</h2>
          <p class="mt-1 text-sm text-[var(--color-text-muted)]">
            The pool of GitHub repos this project's workspaces can clone. Reuse repositories across projects — the
            deploy key lives on the repository.
          </p>
        </div>
        <SubmitButton
          type="button"
          label="Link repository"
          data-testid="project-link-repo-button"
          @click="showPicker = true"
        />
      </div>

      <p v-if="store.repositories.length === 0" class="text-sm text-[var(--color-text-muted)] italic">
        No repositories linked to this project yet.
        <RouterLink to="/repositories" class="text-[var(--color-accent-light)] underline">Add one</RouterLink>
        then come back.
      </p>

      <ul v-else class="space-y-3" data-testid="project-repositories-list">
        <li v-for="r in store.repositories" :key="r.id">
          <Card :data-testid="`project-repository-${r.id}`">
            <template #header>
              <div class="flex items-baseline justify-between">
                <RouterLink :to="`/repositories/${r.id}`" class="font-semibold hover:underline">
                  {{ r.name }}
                </RouterLink>
                <span v-if="!r.deployKeyFingerprint" class="text-xs text-amber-400">no key yet</span>
                <span v-else class="text-xs text-emerald-400">key attached</span>
              </div>
            </template>
            <p class="font-mono text-xs text-[var(--color-text-muted)]">{{ r.repoUrl }}</p>
            <p class="mt-1 text-xs text-[var(--color-text-muted)]">default: {{ r.defaultBranch }}</p>
            <template #footer>
              <div class="flex justify-end">
                <SubmitButton
                  type="button"
                  variant="danger"
                  label="Unlink"
                  :status="unlink.status.value"
                  :data-testid="`project-unlink-${r.id}`"
                  @click="onUnlink(r.id, r.name)"
                />
              </div>
            </template>
          </Card>
        </li>
      </ul>
    </section>

    <Modal :open="showPicker" title="Link a repository" @close="showPicker = false">
      <ProjectRepositoryPicker :already-linked="store.repositories" @pick="onPickRepo" @cancel="showPicker = false" />
    </Modal>
  </div>
</template>
