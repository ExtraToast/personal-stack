import type { CreateWorkspaceInput } from '../services/workspaceService'
import type { AgentKind, AgentSession, Turn, Workspace, WorkspaceDetailWorkspace } from '../types'
import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as workspaceService from '../services/workspaceService'

const ACTIVE_SESSION_STORAGE_KEY = 'assistant-ui:workspace-active-session'

function isLiveSession(session: AgentSession): boolean {
  return session.status === 'STARTING' || session.status === 'RUNNING'
}

function parseStoredSessions(raw: string | null): Record<string, string> {
  if (!raw) return {}
  try {
    const value: unknown = JSON.parse(raw)
    if (!value || typeof value !== 'object' || Array.isArray(value)) return {}
    const result: Record<string, string> = {}
    for (const [key, entry] of Object.entries(value)) {
      if (typeof entry === 'string') result[key] = entry
    }
    return result
  } catch {
    return {}
  }
}

function readPreferredSession(workspaceId: string): string | null {
  try {
    const values = parseStoredSessions(localStorage.getItem(ACTIVE_SESSION_STORAGE_KEY))
    const value = values[workspaceId]
    return value ?? null
  } catch {
    return null
  }
}

function writePreferredSession(workspaceId: string, sessionId: string): void {
  try {
    const values = parseStoredSessions(localStorage.getItem(ACTIVE_SESSION_STORAGE_KEY))
    values[workspaceId] = sessionId
    localStorage.setItem(ACTIVE_SESSION_STORAGE_KEY, JSON.stringify(values))
  } catch {
    // Preference only; keep the store usable when localStorage is unavailable.
  }
}

function chooseActiveSession(all: AgentSession[], preferredId: string | null): string | null {
  const live = all.filter(isLiveSession)
  if (preferredId && live.some((s) => s.id === preferredId)) return preferredId
  if (preferredId && live.length === 0 && all.some((s) => s.id === preferredId)) {
    return preferredId
  }
  return live[0]?.id ?? all[0]?.id ?? null
}

function withRepositoryList(workspace: WorkspaceDetailWorkspace): WorkspaceDetailWorkspace {
  return {
    ...workspace,
    repositories: workspace.repositories ?? [],
  }
}

export const useWorkspacesStore = defineStore('workspaces', () => {
  const workspaces = ref<Workspace[]>([])
  const activeWorkspace = ref<WorkspaceDetailWorkspace | null>(null)
  const sessions = ref<AgentSession[]>([])
  const activeSessionId = ref<string | null>(null)
  const turns = ref<Turn[]>([])
  const isLoading = ref(false)
  const error = ref<string | null>(null)
  // True while a new session's runner is cold-starting (start-session
  // is polling through the runner's not-ready 503 window).
  const startingSession = ref(false)

  async function loadAll(): Promise<void> {
    isLoading.value = true
    error.value = null
    try {
      workspaces.value = await workspaceService.listWorkspaces()
    } catch {
      error.value = 'Failed to load workspaces'
    } finally {
      isLoading.value = false
    }
  }

  async function open(id: string): Promise<void> {
    isLoading.value = true
    error.value = null
    try {
      const detail = await workspaceService.getWorkspace(id)
      const preferredId = activeWorkspace.value?.id === id ? activeSessionId.value : readPreferredSession(id)
      activeWorkspace.value = withRepositoryList(detail.workspace)
      sessions.value = detail.sessions
      activeSessionId.value = chooseActiveSession(sessions.value, preferredId)
      if (activeSessionId.value) {
        writePreferredSession(id, activeSessionId.value)
        await loadTurns(activeSessionId.value)
      } else {
        turns.value = []
      }
    } catch {
      error.value = 'Failed to load workspace'
    } finally {
      isLoading.value = false
    }
  }

  async function create(input: CreateWorkspaceInput): Promise<Workspace> {
    const ws = await workspaceService.createWorkspace(input)
    workspaces.value.unshift(ws)
    return ws
  }

  async function destroy(id: string): Promise<void> {
    await workspaceService.destroyWorkspace(id)
    workspaces.value = workspaces.value.filter((w) => w.id !== id)
    if (activeWorkspace.value?.id === id) {
      activeWorkspace.value = null
      sessions.value = []
      activeSessionId.value = null
      turns.value = []
    }
  }

  async function newSession(kind: AgentKind): Promise<string | null> {
    const ws = activeWorkspace.value
    if (!ws) return null
    startingSession.value = true
    try {
      const { sessionId } = await workspaceService.startSession(ws.id, kind, () => {
        startingSession.value = true
      })
      activeSessionId.value = sessionId
      writePreferredSession(ws.id, sessionId)
      await open(ws.id)
      return sessionId
    } finally {
      startingSession.value = false
    }
  }

  async function endSession(sessionId: string): Promise<void> {
    const ws = activeWorkspace.value
    if (!ws) return
    await workspaceService.stopSession(ws.id, sessionId)
    if (activeSessionId.value === sessionId) activeSessionId.value = null
    await open(ws.id)
  }

  async function loadTurns(sessionId: string): Promise<void> {
    const ws = activeWorkspace.value
    if (!ws) return
    turns.value = await workspaceService.getTurns(ws.id, sessionId)
  }

  async function attachRepository(repositoryId: string): Promise<void> {
    const ws = activeWorkspace.value
    if (!ws) return
    activeWorkspace.value = {
      ...ws,
      repositories: await workspaceService.attachRepository(ws.id, repositoryId),
    }
    await open(ws.id)
  }

  async function detachRepository(repositoryId: string): Promise<void> {
    const ws = activeWorkspace.value
    if (!ws) return
    await workspaceService.detachRepository(ws.id, repositoryId)
    activeWorkspace.value = {
      ...ws,
      repositories: (ws.repositories ?? []).filter((r) => r.id !== repositoryId),
    }
    await open(ws.id)
  }

  function selectSession(sessionId: string): void {
    activeSessionId.value = sessionId
    if (activeWorkspace.value) writePreferredSession(activeWorkspace.value.id, sessionId)
  }

  function appendStreamedOutput(text: string): void {
    if (!activeSessionId.value) return
    const last = turns.value[turns.value.length - 1]
    if (last && last.role === 'AGENT' && last.id.startsWith('stream-')) {
      last.body += text
    } else {
      turns.value.push({
        id: `stream-${crypto.randomUUID()}`,
        sessionId: activeSessionId.value,
        role: 'AGENT',
        body: text,
        createdAt: new Date().toISOString(),
      })
    }
  }

  function appendUserTurn(text: string): void {
    if (!activeSessionId.value) return
    turns.value.push({
      id: `local-${crypto.randomUUID()}`,
      sessionId: activeSessionId.value,
      role: 'USER',
      body: text,
      createdAt: new Date().toISOString(),
    })
  }

  return {
    workspaces,
    activeWorkspace,
    sessions,
    activeSessionId,
    turns,
    isLoading,
    error,
    startingSession,
    loadAll,
    open,
    create,
    destroy,
    newSession,
    endSession,
    loadTurns,
    attachRepository,
    detachRepository,
    selectSession,
    appendStreamedOutput,
    appendUserTurn,
  }
})
