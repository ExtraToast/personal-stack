<script setup lang="ts">
import {
  FormErrors,
  FormField,
  SubmitButton,
  useFormErrors,
  useMutationState,
  useToast,
} from '@personal-stack/vue-common'
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
const selectedPrimaryRepositoryId = ref<string | null>(null)
const selectedRepositoryIds = ref<string[]>([])
const branch = ref('main')
const name = ref('')

const create = useMutationState<void>()
// `formErrors` carries the structured error from the last failed
// submit so the wizard can render an inline banner + per-field
// error messages (per the ProblemDetail extensions from PRs #427
// / #428) instead of just a top-right toast.
const formErrors = useFormErrors()

onMounted(async () => {
  try {
    await projects.loadAll()
  } catch (e) {
    toast.errorFromCatch('Could not load projects', e)
  }
})

watch(selectedProjectId, async (id) => {
  if (!id) return
  selectedPrimaryRepositoryId.value = null
  selectedRepositoryIds.value = []
  try {
    await projects.open(id)
  } catch (e) {
    toast.errorFromCatch('Could not load that project', e)
  }
})

watch(selectedPrimaryRepositoryId, async (id) => {
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
const selectedRepo = computed(() => {
  const primaryId = selectedPrimaryRepositoryId.value
  if (!primaryId) return null

  const loadedRepo = repos.detailById[primaryId]?.repository
  if (loadedRepo) return loadedRepo

  return projectRepos.value.find((r) => r.id === primaryId) ?? null
})
const selectedRepositories = computed(() => {
  const ids = new Set(selectedRepositoryIds.value)
  return projectRepos.value.filter((r) => ids.has(r.id))
})
const additionalRepositoriesMissingKeys = computed(() =>
  selectedRepositories.value.filter((r) => r.id !== selectedPrimaryRepositoryId.value && !r.deployKeyFingerprint),
)
const keyAttached = computed(() => Boolean(selectedRepo.value?.deployKeyFingerprint))
const selectedRepositoryCount = computed(() => selectedRepositoryIds.value.length)

function ensureRepositorySelected(repositoryId: string): void {
  if (selectedRepositoryIds.value.includes(repositoryId)) return
  selectedRepositoryIds.value = [...selectedRepositoryIds.value, repositoryId]
}

function checked(event: Event): boolean {
  return event.target instanceof HTMLInputElement && event.target.checked
}

function onRepositorySelectionChange(repositoryId: string, isSelected: boolean): void {
  if (isSelected) {
    ensureRepositorySelected(repositoryId)
    if (!selectedPrimaryRepositoryId.value) selectedPrimaryRepositoryId.value = repositoryId
    return
  }

  selectedRepositoryIds.value = selectedRepositoryIds.value.filter((id) => id !== repositoryId)
  if (selectedPrimaryRepositoryId.value === repositoryId) {
    selectedPrimaryRepositoryId.value = selectedRepositoryIds.value[0] ?? null
  }
}

function onPrimaryRepositoryChange(repositoryId: string): void {
  selectedPrimaryRepositoryId.value = repositoryId
  ensureRepositorySelected(repositoryId)
}

async function onSubmit(): Promise<void> {
  if (!selectedProjectId.value || !selectedPrimaryRepositoryId.value || !name.value.trim()) return
  formErrors.clear()
  try {
    await create.run(async () => {
      const ws = await workspaces.create({
        name: name.value.trim(),
        kind: 'REPO_BACKED',
        projectId: selectedProjectId.value,
        repositoryId: selectedPrimaryRepositoryId.value,
        primaryRepositoryId: selectedPrimaryRepositoryId.value,
        repositoryIds: selectedRepositoryIds.value,
        branch: branch.value.trim() || 'main',
      })
      emit('created', ws.id)
      toast.success('Workspace created', `Booting ${ws.name}…`)
      await router.push(`/sessions/workspace/${ws.id}`)
    })
    // Reset for next time.
    step.value = 'pick-project'
    selectedProjectId.value = null
    selectedPrimaryRepositoryId.value = null
    selectedRepositoryIds.value = []
    branch.value = 'main'
    name.value = ''
  } catch (e) {
    // Surface the structured error inline at the form (banner +
    // per-field). The toast stays for non-form failures (background
    // refreshes, navigation-time loads); keeping it on the submit
    // path would duplicate the same content in two places.
    formErrors.captureFromCatch(e)
  }
}
</script>

<template>
  <div v-if="open" class="space-y-6" data-testid="create-workspace-wizard">
    <ol class="flex items-center gap-2 text-xs text-[var(--color-text-muted)]">
      <li :class="step === 'pick-project' ? 'text-[var(--color-accent-light)]' : ''">1. Project</li>
      <span>→</span>
      <li :class="step === 'pick-repo' ? 'text-[var(--color-accent-light)]' : ''">2. Repositories</li>
      <span>→</span>
      <li :class="step === 'pick-branch' ? 'text-[var(--color-accent-light)]' : ''">3. Branch + name</li>
    </ol>

    <section v-if="step === 'pick-project'" class="space-y-3">
      <p class="text-sm text-[var(--color-text-muted)]">
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
              <p class="font-mono text-xs text-[var(--color-text-muted)]">project:{{ p.slug }}</p>
            </div>
          </label>
        </li>
      </ul>
      <p v-else class="text-sm text-[var(--color-text-muted)] italic">
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
      <p class="text-sm text-[var(--color-text-muted)]">
        Select the repositories to clone into this workspace, then choose one primary repository for the branch and
        split workflow defaults. Need a different one?
        <RouterLink to="/repositories" class="text-[var(--color-accent-light)] underline">
          Add a repository
        </RouterLink>
        and link it to the project first.
      </p>
      <ul v-if="projectRepos.length > 0" class="space-y-2 max-h-72 overflow-y-auto">
        <li v-for="r in projectRepos" :key="r.id">
          <div class="rounded-md border border-[var(--color-surface-border)] bg-[var(--color-surface-card)] p-3">
            <label class="flex cursor-pointer items-start gap-3">
              <input
                :checked="selectedRepositoryIds.includes(r.id)"
                type="checkbox"
                :value="r.id"
                class="mt-1"
                :data-testid="`wizard-repo-checkbox-${r.id}`"
                @change="onRepositorySelectionChange(r.id, checked($event))"
              />
              <div class="min-w-0 flex-1">
                <div class="flex items-baseline justify-between">
                  <span class="font-semibold">{{ r.name }}</span>
                  <span v-if="!r.deployKeyFingerprint" class="text-xs text-amber-400">no key yet</span>
                  <span v-else class="text-xs text-emerald-400">key attached</span>
                </div>
                <p class="font-mono text-xs text-[var(--color-text-muted)]">{{ r.repoUrl }}</p>
              </div>
            </label>
            <label class="mt-3 flex cursor-pointer items-center gap-2 pl-7 text-xs text-[var(--color-text-muted)]">
              <input
                :checked="selectedPrimaryRepositoryId === r.id"
                type="radio"
                name="workspace-primary-repository"
                :value="r.id"
                :data-testid="`wizard-repo-primary-${r.id}`"
                @change="onPrimaryRepositoryChange(r.id)"
              />
              Primary repository
            </label>
          </div>
        </li>
      </ul>
      <p v-else class="text-sm text-[var(--color-text-muted)] italic">No repositories linked to this project yet.</p>

      <div
        v-if="selectedRepo && !keyAttached"
        class="rounded-md border border-amber-500/40 bg-amber-500/5 p-3 text-sm text-amber-300"
        data-testid="wizard-missing-key-warning"
      >
        The primary repository has no deploy key yet — the runner Pod won't be able to clone it.
        <RouterLink :to="`/repositories/${selectedPrimaryRepositoryId}`" class="underline">Attach a key</RouterLink>
        first.
      </div>

      <div
        v-if="additionalRepositoriesMissingKeys.length > 0"
        class="rounded-md border border-amber-500/40 bg-amber-500/5 p-3 text-sm text-amber-300"
        data-testid="wizard-missing-selected-keys-warning"
      >
        {{ additionalRepositoriesMissingKeys.length }} selected
        {{ additionalRepositoriesMissingKeys.length === 1 ? 'repository has' : 'repositories have' }} no deploy key yet;
        additional repositories clone with the GitHub App token when the runner starts.
      </div>

      <div class="flex justify-end gap-2">
        <SubmitButton type="button" variant="secondary" label="Back" @click="step = 'pick-project'" />
        <SubmitButton
          type="button"
          :label="selectedRepositoryCount > 1 ? `Next (${selectedRepositoryCount} repos)` : 'Next'"
          :disabled="!selectedPrimaryRepositoryId || !keyAttached"
          data-testid="wizard-step2-next"
          @click="step = 'pick-branch'"
        />
      </div>
    </section>

    <section v-else-if="step === 'pick-branch'" class="space-y-3">
      <FormErrors :error="formErrors.general.value" />

      <FormField
        label="Workspace name"
        required
        :error="formErrors.fieldErrorFor('name')"
        hint="Shown on the workspace card + used as the Pod name suffix."
      >
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

      <FormField
        label="Branch"
        :error="formErrors.fieldErrorFor('branch')"
        hint="The runner checks this out after the clone."
      >
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
