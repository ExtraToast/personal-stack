<script setup lang="ts">
import type { AgentKind } from '../types'
import { Modal, useToast } from '@personal-stack/vue-common'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AgentKindPicker from '../components/AgentKindPicker.vue'
import SessionTabs from '../components/SessionTabs.vue'
import SessionTerminal from '../components/SessionTerminal.vue'
import WorkspaceRepositoriesPanel from '../components/WorkspaceRepositoriesPanel.vue'
import WorkspaceRepositoryPicker from '../components/WorkspaceRepositoryPicker.vue'
import WorkspaceSplitGuidance from '../components/WorkspaceSplitGuidance.vue'
import { sendInput, stageInput } from '../services/workspaceService'
import { useWorkspacesStore } from '../stores/workspaces'

const route = useRoute()
const router = useRouter()
const store = useWorkspacesStore()
const toast = useToast()

const workspaceId = computed(() => String(route.params.id))
const pickerKind = ref<AgentKind>('CLAUDE')
const showStageInput = ref(false)
const showRepositoryPicker = ref(false)
const stageName = ref('source.txt')
const stageContent = ref('')
const isStaging = ref(false)
const isSendingSplitCommand = ref(false)
const isAttachingRepository = ref(false)
const detachingRepositoryId = ref<string | null>(null)
const repositoryActionError = ref<string | null>(null)

// Only sessions with a live PTY get a mounted terminal. A session
// dropping out of this set (STOPPED/FAILED) unmounts its
// SessionTerminal, which closes the socket and disposes xterm.
const liveSessions = computed(() => store.sessions.filter((s) => s.status === 'STARTING' || s.status === 'RUNNING'))
const activeLiveSession = computed(() => liveSessions.value.find((s) => s.id === store.activeSessionId) ?? null)
const activeStageSession = computed(() => {
  const session = activeLiveSession.value
  return session?.status === 'RUNNING' && session.gatewayAgentId ? session : null
})
const agentKindLabels: Record<AgentKind, string> = {
  CLAUDE: 'Claude Code',
  CODEX: 'Codex',
  SHELL: 'shell',
}
const spawnButtonLabel = computed(() =>
  store.startingSession ? 'Starting runner…' : `Start ${agentKindLabels[pickerKind.value]}`,
)

onMounted(async () => {
  await store.open(workspaceId.value)
})

async function onSpawn(): Promise<void> {
  await store.newSession(pickerKind.value)
}

async function onStopSession(id: string): Promise<void> {
  await store.endSession(id)
}

function closeStageInput(): void {
  if (isStaging.value) return
  showStageInput.value = false
  stageContent.value = ''
}

async function onStageInput(): Promise<void> {
  const ws = store.activeWorkspace
  const session = activeStageSession.value
  if (!ws || !session || stageContent.value.length === 0) return
  isStaging.value = true
  try {
    const staged = await stageInput(ws.id, session.id, stageContent.value, stageName.value)
    const prompt = `Please read ${staged.path} and use it as the source document for the next task.`
    await sendInput(ws.id, session.id, prompt, true)
    toast.success('Text staged', staged.path)
    showStageInput.value = false
    stageContent.value = ''
  } catch (e) {
    toast.errorFromCatch('Could not stage text', e)
  } finally {
    isStaging.value = false
  }
}

async function onSendSplitCommand(command: string): Promise<void> {
  const ws = store.activeWorkspace
  const session = activeStageSession.value
  if (!ws || !session || !command.trim()) return
  isSendingSplitCommand.value = true
  try {
    await sendInput(ws.id, session.id, command.trim(), true)
    toast.success('Split command sent')
  } catch (e) {
    toast.errorFromCatch('Could not send split command', e)
  } finally {
    isSendingSplitCommand.value = false
  }
}

async function onAttachRepository(repositoryId: string): Promise<void> {
  repositoryActionError.value = null
  isAttachingRepository.value = true
  try {
    await store.attachRepository(repositoryId)
    showRepositoryPicker.value = false
    toast.success('Repository attached')
  } catch (e) {
    repositoryActionError.value = 'Could not attach the repository'
    toast.errorFromCatch('Could not attach the repository', e)
  } finally {
    isAttachingRepository.value = false
  }
}

async function onDetachRepository(repositoryId: string, repositoryName: string): Promise<void> {
  repositoryActionError.value = null
  detachingRepositoryId.value = repositoryId
  try {
    await store.detachRepository(repositoryId)
    toast.success(`Removed ${repositoryName}`)
  } catch (e) {
    repositoryActionError.value = 'Could not remove the repository'
    toast.errorFromCatch('Could not remove the repository', e)
  } finally {
    detachingRepositoryId.value = null
  }
}
</script>

<template>
  <!-- Fill the viewport BELOW the fixed AppShell nav (h-14 / 3.5rem),
       not a full 100vh: a full-height child inside the shell's pt-14
       main overflowed by the nav height, so the page scrolled on top
       of the terminal's own scroll. Sizing to the remaining space keeps
       a single scroll region (the xterm viewport). -->
  <div class="flex flex-col h-[calc(100vh-3.5rem)] overflow-hidden">
    <header
      class="flex items-center border-b border-[var(--color-surface-border)] px-6 py-3"
      data-testid="workspace-view-header"
    >
      <div class="min-w-0">
        <button
          type="button"
          class="text-sm text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)] mb-1"
          @click="router.push('/sessions')"
        >
          ← Sessions
        </button>
        <h1 class="text-xl font-bold">
          {{ store.activeWorkspace?.name ?? 'Loading…' }}
        </h1>
        <p v-if="store.activeWorkspace?.repoUrl" class="truncate text-xs text-[var(--color-text-muted)] font-mono">
          {{ store.activeWorkspace.repoUrl }}
        </p>
      </div>
    </header>

    <main class="flex min-h-0 flex-1 flex-col overflow-hidden lg:flex-row">
      <section class="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden p-4">
        <!-- One terminal per live session, all kept mounted; v-show (not
             v-if/:key) so switching tabs preserves each xterm buffer and
             its WebSocket. A session leaving the live set (stopped/failed)
             unmounts, which disposes its terminal + socket. -->
        <SessionTerminal
          v-for="s in liveSessions"
          v-show="s.id === store.activeSessionId"
          :key="s.id"
          :session-id="s.id"
          :active="s.id === store.activeSessionId"
        />
        <div
          v-if="liveSessions.length === 0"
          class="flex flex-1 items-center justify-center rounded-md border border-dashed border-[var(--color-surface-border)] text-center text-sm italic text-[var(--color-text-muted)]"
        >
          Start an agent from the sidebar.
        </div>
      </section>

      <aside
        v-if="store.activeWorkspace"
        class="flex min-h-0 w-full shrink-0 flex-col gap-4 overflow-y-auto border-t border-[var(--color-surface-border)] bg-[var(--color-surface-card)] p-4 lg:w-[23rem] lg:border-l lg:border-t-0"
        data-testid="workspace-sidebar"
        aria-label="Workspace controls"
      >
        <section
          class="rounded-md border border-[var(--color-surface-border)] bg-[var(--color-surface-card)] p-4"
          data-testid="workspace-agent-panel"
        >
          <div class="mb-3 flex items-center justify-between gap-3">
            <h2 class="text-sm font-semibold">Agents</h2>
            <span
              class="rounded border border-[var(--color-surface-border)] px-2 py-0.5 text-xs text-[var(--color-text-muted)]"
            >
              {{ liveSessions.length }} live
            </span>
          </div>
          <AgentKindPicker v-model="pickerKind" compact />
          <button
            type="button"
            class="mt-3 inline-flex min-h-11 w-full items-center justify-center rounded-md border border-transparent bg-[var(--color-accent)] px-4 py-2 text-sm font-medium text-white shadow-sm transition-colors hover:bg-[var(--color-accent-light)] focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-accent-light)] focus-visible:ring-offset-2 focus-visible:ring-offset-[var(--color-surface-dark)] disabled:cursor-not-allowed disabled:opacity-60"
            :disabled="store.startingSession"
            data-testid="workspace-new-agent"
            aria-label="Start a new agent session"
            @click="onSpawn"
          >
            {{ spawnButtonLabel }}
          </button>
        </section>

        <section
          class="rounded-md border border-[var(--color-surface-border)] bg-[var(--color-surface-card)] p-4"
          data-testid="workspace-tools-panel"
        >
          <h2 class="text-sm font-semibold">Tools</h2>
          <p id="stage-input-hint" class="sr-only">
            Stage text is available when the active session is running and attached to a gateway.
          </p>
          <button
            type="button"
            class="mt-3 inline-flex min-h-10 w-full items-center justify-center rounded-md border border-[var(--color-surface-border)] bg-[var(--color-surface-elevated)] px-3 py-2 text-sm font-medium text-[var(--color-text-primary)] transition-colors hover:bg-[var(--color-surface-border)] focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-accent-light)] focus-visible:ring-offset-2 focus-visible:ring-offset-[var(--color-surface-dark)] disabled:cursor-not-allowed disabled:opacity-60"
            :disabled="!activeStageSession || isStaging"
            data-testid="stage-input-open"
            aria-describedby="stage-input-hint"
            @click="showStageInput = true"
          >
            {{ isStaging ? 'Staging…' : 'Stage text' }}
          </button>
        </section>

        <section
          class="rounded-md border border-[var(--color-surface-border)] bg-[var(--color-surface-card)] p-4"
          data-testid="workspace-sessions-panel"
        >
          <div class="mb-3 flex items-center justify-between gap-3">
            <h2 class="text-sm font-semibold">Sessions</h2>
            <span class="text-xs text-[var(--color-text-muted)]">{{ store.sessions.length }}</span>
          </div>
          <SessionTabs
            :sessions="store.sessions"
            :active-id="store.activeSessionId"
            orientation="vertical"
            @select="store.selectSession"
            @stop="onStopSession"
          />
        </section>

        <WorkspaceRepositoriesPanel
          :repositories="store.activeWorkspace.repositories ?? []"
          :attach-pending="isAttachingRepository"
          :detach-pending-id="detachingRepositoryId"
          :error="repositoryActionError"
          @add="showRepositoryPicker = true"
          @detach="onDetachRepository"
        />
        <WorkspaceSplitGuidance
          :repositories="store.activeWorkspace.repositories ?? []"
          :project-id="store.activeWorkspace.projectId"
          :can-send="Boolean(activeStageSession)"
          :send-pending="isSendingSplitCommand"
          @add-destination="showRepositoryPicker = true"
          @send-command="onSendSplitCommand"
        />
      </aside>
    </main>

    <Modal :open="showRepositoryPicker" title="Attach repository" @close="showRepositoryPicker = false">
      <WorkspaceRepositoryPicker
        :already-attached="store.activeWorkspace?.repositories ?? []"
        :pending="isAttachingRepository"
        @pick="onAttachRepository"
        @cancel="showRepositoryPicker = false"
      />
    </Modal>

    <Modal :open="showStageInput" title="Stage text" @close="closeStageInput">
      <form class="space-y-4" data-testid="stage-input-form" @submit.prevent="onStageInput">
        <label class="block space-y-1 text-sm">
          <span class="text-[var(--color-text-muted)]">File name</span>
          <input
            v-model="stageName"
            type="text"
            class="w-full rounded border border-[var(--color-surface-border)] bg-[var(--color-surface)] px-3 py-2"
            data-testid="stage-input-name"
          />
        </label>
        <label class="block space-y-1 text-sm">
          <span class="text-[var(--color-text-muted)]">Text</span>
          <textarea
            v-model="stageContent"
            class="min-h-72 w-full rounded border border-[var(--color-surface-border)] bg-[var(--color-surface)] px-3 py-2 font-mono text-sm"
            data-testid="stage-input-content"
          />
        </label>
        <div class="flex justify-end gap-2">
          <button
            type="button"
            class="rounded-md border border-[var(--color-surface-border)] bg-[var(--color-surface-elevated)] px-4 py-2 text-sm transition-colors hover:bg-[var(--color-surface-border)] focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-accent-light)] focus-visible:ring-offset-2 focus-visible:ring-offset-[var(--color-surface-dark)] disabled:cursor-not-allowed disabled:opacity-60"
            :disabled="isStaging"
            @click="closeStageInput"
          >
            Cancel
          </button>
          <button
            type="submit"
            class="rounded-md bg-[var(--color-accent)] px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-[var(--color-accent-light)] focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-accent-light)] focus-visible:ring-offset-2 focus-visible:ring-offset-[var(--color-surface-dark)] disabled:cursor-not-allowed disabled:opacity-60"
            :disabled="isStaging || stageContent.length === 0"
            data-testid="stage-input-submit"
          >
            {{ isStaging ? 'Staging…' : 'Stage' }}
          </button>
        </div>
      </form>
    </Modal>
  </div>
</template>
