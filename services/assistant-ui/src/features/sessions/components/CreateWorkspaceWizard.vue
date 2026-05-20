<script setup lang="ts">
import { FormField, SubmitButton, useMutationState, useToast } from '@personal-stack/vue-common'
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useProjectsStore } from '@/features/projects'
import { useRepositoriesStore } from '@/features/repositories'
import { useWorkspacesStore } from '@/features/workspaces'

interface Props {
  open: boolean
}

defineProps<Props>()

const emit = defineEmits<{
  close: []
  created: [workspaceId: string]
}>()

const projects = useProjectsStore()
const repos = useRepositoriesStore()
const workspaces = useWorkspacesStore()
const router = useRouter()
const toast = useToast()

const step = ref<'pick-project' | 'pick-repo' | 'pick-branch'>('pick-project')
const selectedProjectId = ref<string | null>(null)
const selectedRepositoryId = ref<string | null>(null)
const branch = ref('main')
const name = ref('')

const create = useMutationState<void>()

onMounted(async () => {
  try {
    await projects.loadAll()
  } catch (e) {
    toast.errorFromCatch('Could not load projects', e)
  }
})

watch(selectedProjectId, async (id) => {
  if (!id) return
  selectedRepositoryId.value = null
  try {
    await projects.open(id)
  } catch (e) {
    toast.errorFromCatch('Could not load that project', e)
  }
})

watch(selectedRepositoryId, async (id) => {
  if (!id) return
  // Load detail so we can read the default branch + key state.
  try {
    await repos.loadDetail(id)
    const repo = repos.detailById[id]?.repository
    if (repo) {
      branch.value = repo.defaultBranch
      if (!name.value) name.value = repo.name
    }
  } catch (e) {
    toast.errorFromCatch('Could not load repository', e)
  }
})

const projectRepos = computed(() => projects.repositories)
const selectedRepo = computed(() =>
  selectedRepositoryId.value ? (repos.detailById[selectedRepositoryId.value]?.repository ?? null) : null,
)
const keyAttached = computed(() => Boolean(selectedRepo.value?.deployKeyFingerprint))

async function onSubmit(): Promise<void> {
  if (!selectedProjectId.value || !selectedRepositoryId.value || !name.value.trim()) return
  try {
    await create.run(async () => {
      const ws = await workspaces.create({
        name: name.value.trim(),
        kind: 'REPO_BACKED',
        projectId: selectedProjectId.value,
        repositoryId: selectedRepositoryId.value,
        branch: branch.value.trim() || 'main',
      })
      emit('created', ws.id)
      toast.success('Workspace created', `Booting ${ws.name}…`)
      await router.push(`/sessions/workspace/${ws.id}`)
    })
    // Reset for next time.
    step.value = 'pick-project'
    selectedProjectId.value = null
    selectedRepositoryId.value = null
    branch.value = 'main'
    name.value = ''
  } catch (e) {
    toast.errorFromCatch('Could not create the workspace', e)
  }
}
</script>

<template>
  <div v-if="open" class="space-y-6" data-testid="create-workspace-wizard">
    <ol class="flex items-center gap-2 text-xs text-gray-500">
      <li :class="step === 'pick-project' ? 'text-[var(--color-accent-light)]' : ''">1. Project</li>
      <span>→</span>
      <li :class="step === 'pick-repo' ? 'text-[var(--color-accent-light)]' : ''">2. Repository</li>
      <span>→</span>
      <li :class="step === 'pick-branch' ? 'text-[var(--color-accent-light)]' : ''">3. Branch + name</li>
    </ol>

    <section v-if="step === 'pick-project'" class="space-y-3">
      <p class="text-sm text-gray-400">
        Pick the project this workspace belongs to. The next step shows that project's repository pool.
      </p>
      <ul v-if="projects.projects.length > 0" class="space-y-2 max-h-72 overflow-y-auto">
        <li v-for="p in projects.projects" :key="p.id">
          <label
            class="flex items-baseline gap-3 rounded-md border border-[var(--color-surface-border)] bg-[var(--color-surface-card)] p-3 cursor-pointer hover:border-[var(--color-accent)]"
          >
            <input
              v-model="selectedProjectId"
              type="radio"
              :value="p.id"
              class="mt-1"
              :data-testid="`wizard-project-${p.id}`"
            />
            <div class="flex-1">
              <p class="font-semibold">{{ p.name }}</p>
              <p class="font-mono text-xs text-gray-500">project:{{ p.slug }}</p>
            </div>
          </label>
        </li>
      </ul>
      <p v-else class="text-sm text-gray-500 italic">
        No projects yet.
        <RouterLink to="/projects" class="text-[var(--color-accent-light)] underline">Create one</RouterLink>
        then come back.
      </p>
      <div class="flex justify-end gap-2">
        <SubmitButton type="button" variant="secondary" label="Cancel" @click="emit('close')" />
        <SubmitButton
          type="button"
          label="Next"
          :disabled="!selectedProjectId"
          data-testid="wizard-step1-next"
          @click="step = 'pick-repo'"
        />
      </div>
    </section>

    <section v-else-if="step === 'pick-repo'" class="space-y-3">
      <p class="text-sm text-gray-400">
        Pick a repository from the project's pool. Need a different one?
        <RouterLink to="/repositories" class="text-[var(--color-accent-light)] underline">
          Add a repository
        </RouterLink>
        and link it to the project first.
      </p>
      <ul v-if="projectRepos.length > 0" class="space-y-2 max-h-72 overflow-y-auto">
        <li v-for="r in projectRepos" :key="r.id">
          <label
            class="flex items-baseline gap-3 rounded-md border border-[var(--color-surface-border)] bg-[var(--color-surface-card)] p-3 cursor-pointer hover:border-[var(--color-accent)]"
          >
            <input
              v-model="selectedRepositoryId"
              type="radio"
              :value="r.id"
              class="mt-1"
              :data-testid="`wizard-repo-${r.id}`"
            />
            <div class="flex-1">
              <div class="flex items-baseline justify-between">
                <span class="font-semibold">{{ r.name }}</span>
                <span v-if="!r.deployKeyFingerprint" class="text-xs text-amber-400">no key yet</span>
                <span v-else class="text-xs text-emerald-400">key attached</span>
              </div>
              <p class="font-mono text-xs text-gray-500">{{ r.repoUrl }}</p>
            </div>
          </label>
        </li>
      </ul>
      <p v-else class="text-sm text-gray-500 italic">No repositories linked to this project yet.</p>

      <div
        v-if="selectedRepo && !keyAttached"
        class="rounded-md border border-amber-500/40 bg-amber-500/5 p-3 text-sm text-amber-300"
        data-testid="wizard-missing-key-warning"
      >
        The selected repository has no deploy key yet — the runner Pod won't be able to clone it.
        <RouterLink :to="`/repositories/${selectedRepositoryId}`" class="underline">Attach a key</RouterLink>
        first.
      </div>

      <div class="flex justify-end gap-2">
        <SubmitButton type="button" variant="secondary" label="Back" @click="step = 'pick-project'" />
        <SubmitButton
          type="button"
          label="Next"
          :disabled="!selectedRepositoryId || !keyAttached"
          data-testid="wizard-step2-next"
          @click="step = 'pick-branch'"
        />
      </div>
    </section>

    <section v-else-if="step === 'pick-branch'" class="space-y-3">
      <FormField label="Workspace name" required hint="Shown on the workspace card + used as the Pod name suffix.">
        <template #default="{ id }">
          <input
            :id="id"
            v-model="name"
            type="text"
            required
            maxlength="80"
            class="w-full rounded border border-[var(--color-surface-border)] bg-[var(--color-surface-card)] px-3 py-2 text-sm"
            placeholder="feature-foo"
            data-testid="wizard-name"
          />
        </template>
      </FormField>

      <FormField label="Branch" hint="The runner checks this out after the clone.">
        <template #default="{ id }">
          <input
            :id="id"
            v-model="branch"
            type="text"
            maxlength="120"
            class="w-full rounded border border-[var(--color-surface-border)] bg-[var(--color-surface-card)] px-3 py-2 font-mono text-xs"
            placeholder="main"
            data-testid="wizard-branch"
          />
        </template>
      </FormField>

      <div class="flex justify-end gap-2">
        <SubmitButton type="button" variant="secondary" label="Back" @click="step = 'pick-repo'" />
        <SubmitButton
          type="button"
          label="Open workspace"
          :status="create.status.value"
          :disabled="!name.trim()"
          data-testid="wizard-submit"
          @click="onSubmit"
        />
      </div>
    </section>
  </div>
</template>
