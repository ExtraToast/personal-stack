import type { AgentKind, AgentSession, Turn, Workspace } from '../types'
import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  createWorkspace,
  destroyWorkspace,
  getTurns,
  getWorkspace,
  listWorkspaces,
  startSession,
  stopSession,
} from '../services/workspaceService'

export const useWorkspacesStore = defineStore('workspaces', () => {
  const workspaces = ref<Workspace[]>([])
  const activeWorkspace = ref<Workspace | null>(null)
  const sessions = ref<AgentSession[]>([])
  const activeSessionId = ref<string | null>(null)
  const turns = ref<Turn[]>([])
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  async function loadAll(): Promise<void> {
    isLoading.value = true
    error.value = null
    try {
      workspaces.value = await listWorkspaces()
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
      const detail = await getWorkspace(id)
      activeWorkspace.value = detail.workspace
      sessions.value = detail.sessions
      if (sessions.value.length > 0 && !activeSessionId.value) {
        activeSessionId.value = sessions.value[0]!.id
        await loadTurns(activeSessionId.value)
      }
    } catch {
      error.value = 'Failed to load workspace'
    } finally {
      isLoading.value = false
    }
  }

  async function create(input: { name: string; repoUrl?: string | null; branch?: string | null }): Promise<Workspace> {
    const ws = await createWorkspace(input)
    workspaces.value.unshift(ws)
    return ws
  }

  async function destroy(id: string): Promise<void> {
    await destroyWorkspace(id)
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
    const { sessionId } = await startSession(ws.id, kind)
    activeSessionId.value = sessionId
    await open(ws.id)
    return sessionId
  }

  async function endSession(sessionId: string): Promise<void> {
    const ws = activeWorkspace.value
    if (!ws) return
    await stopSession(ws.id, sessionId)
    if (activeSessionId.value === sessionId) activeSessionId.value = null
    await open(ws.id)
  }

  async function loadTurns(sessionId: string): Promise<void> {
    const ws = activeWorkspace.value
    if (!ws) return
    turns.value = await getTurns(ws.id, sessionId)
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
    loadAll,
    open,
    create,
    destroy,
    newSession,
    endSession,
    loadTurns,
    appendStreamedOutput,
    appendUserTurn,
  }
})
