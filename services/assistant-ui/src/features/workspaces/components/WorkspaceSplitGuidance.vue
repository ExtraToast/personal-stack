<script setup lang="ts">
import type { WorkspaceRepository } from '../types'
import { computed, ref, watch } from 'vue'

interface Props {
  repositories: WorkspaceRepository[]
  projectId: string | null
  canSend?: boolean
  sendPending?: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  addDestination: []
  sendCommand: [command: string]
}>()

const splitPath = ref('path/to/subtree')
const selectedDestinationId = ref<string | null>(null)

const primaryRepository = computed(() => props.repositories.find((r) => r.isPrimary) ?? props.repositories[0] ?? null)
const destinationRepositories = computed(() => props.repositories.filter((r) => r.id !== primaryRepository.value?.id))
const selectedDestination = computed(() => {
  const selected = destinationRepositories.value.find((r) => r.id === selectedDestinationId.value)
  return selected ?? destinationRepositories.value[0] ?? null
})

watch(
  destinationRepositories,
  (repos) => {
    if (repos.length === 0) {
      selectedDestinationId.value = null
      return
    }
    if (!selectedDestinationId.value || !repos.some((r) => r.id === selectedDestinationId.value)) {
      selectedDestinationId.value = repos[0]?.id ?? null
    }
  },
  { immediate: true },
)

const destinationSlug = computed(() => (selectedDestination.value ? repositorySlug(selectedDestination.value) : null))
const primaryRepositoryDir = computed(() =>
  primaryRepository.value ? repositoryDirName(primaryRepository.value) : '<primary-repo>',
)
const commandPath = computed(() => splitPath.value.trim() || '<p>')
const splitCommand = computed(() =>
  destinationSlug.value
    ? `cd /workspace/${primaryRepositoryDir.value} && council split --path ${commandPath.value} --dest ${destinationSlug.value}`
    : '',
)
const inProject = computed(() => Boolean(props.projectId))

function repositoryDirName(repository: WorkspaceRepository): string {
  return (
    repository.repoUrl
      .trim()
      .replace(/\.git$/, '')
      .split('/')
      .pop() || repository.name
  )
}

function repositorySlug(repository: WorkspaceRepository): string {
  const trimmed = repository.repoUrl.trim().replace(/\.git$/, '')
  const match = trimmed.match(/github\.com[:/]([^/\s:]+)\/([^/\s]+)$/)
  if (match) return `${match[1]}/${match[2]}`
  return repository.name
}
</script>

<template>
  <section
    class="rounded-md border border-[var(--color-surface-border)] bg-[var(--color-surface-card)] p-4"
    data-testid="workspace-split-guidance"
  >
    <h2 class="text-sm font-semibold">Split guidance</h2>
    <p class="mt-1 text-xs text-[var(--color-text-muted)]">
      This assembles a command for the terminal. It does not run the split or start a runner.
    </p>

    <p v-if="!primaryRepository" class="mt-3 text-sm text-[var(--color-text-muted)] italic">
      Attach a primary repository before preparing a split.
    </p>

    <div v-else-if="destinationRepositories.length === 0" class="mt-3 space-y-3">
      <p class="text-sm text-[var(--color-text-muted)] italic">
        Attach a destination repository to build a split command.
      </p>
      <button
        type="button"
        class="w-full rounded-md border border-[var(--color-surface-border)] bg-[var(--color-surface-elevated)] px-3 py-2 text-sm font-medium text-[var(--color-text-primary)] transition-colors hover:bg-[var(--color-surface-border)] focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-accent-light)] focus-visible:ring-offset-2 focus-visible:ring-offset-[var(--color-surface-dark)] disabled:cursor-not-allowed disabled:opacity-60"
        data-testid="split-attach-destination"
        @click="emit('addDestination')"
      >
        Attach destination repository
      </button>
    </div>

    <div v-else class="mt-3 space-y-3">
      <label class="block space-y-1 text-xs">
        <span class="text-[var(--color-text-muted)]">Path to split</span>
        <input
          v-model="splitPath"
          type="text"
          class="w-full rounded border border-[var(--color-surface-border)] bg-[var(--color-surface)] px-3 py-2 font-mono text-xs"
          data-testid="split-path-input"
        />
      </label>

      <label class="block space-y-1 text-xs">
        <span class="text-[var(--color-text-muted)]">Destination repository</span>
        <select
          v-model="selectedDestinationId"
          class="w-full rounded border border-[var(--color-surface-border)] bg-[var(--color-surface)] px-3 py-2 text-xs"
          data-testid="split-destination-select"
        >
          <option v-for="r in destinationRepositories" :key="r.id" :value="r.id">
            {{ repositorySlug(r) }}
          </option>
        </select>
      </label>

      <pre
        class="overflow-x-auto rounded bg-[#0b0e14] p-3 font-mono text-xs text-[var(--color-terminal-cyan)]"
      ><code data-testid="workspace-split-command">{{ splitCommand }}</code></pre>

      <button
        type="button"
        class="w-full rounded-md bg-[var(--color-accent)] px-3 py-2 text-sm font-medium text-white transition-colors hover:bg-[var(--color-accent-light)] focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-accent-light)] focus-visible:ring-offset-2 focus-visible:ring-offset-[var(--color-surface-dark)] disabled:cursor-not-allowed disabled:opacity-60"
        :disabled="!canSend || sendPending"
        data-testid="split-send-command"
        @click="emit('sendCommand', splitCommand)"
      >
        {{ sendPending ? 'Sending...' : 'Paste command into session' }}
      </button>

      <ol class="list-decimal space-y-1 pl-4 text-xs text-[var(--color-text-muted)]" data-testid="split-follow-up">
        <li>Run the command from the primary clone, /workspace/{{ primaryRepositoryDir }}.</li>
        <li v-if="inProject">
          Keep {{ destinationSlug }} linked in the project repository pool before opening follow-up workspaces.
        </li>
        <li v-else>Keep {{ destinationSlug }} attached here, or open a new workspace from that repository.</li>
        <li>Start the next runner from {{ destinationSlug }} after the split lands.</li>
      </ol>
    </div>
  </section>
</template>
