import type { Workspace, WorkspaceDetail } from '../types'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createWorkspace,
  destroyWorkspace,
  getTurns,
  getWorkspace,
  listWorkspaces,
  startSession,
} from '../services/workspaceService'

import { useWorkspacesStore } from '../stores/workspaces'

vi.mock('../services/workspaceService', () => ({
  listWorkspaces: vi.fn(),
  getWorkspace: vi.fn(),
  createWorkspace: vi.fn(),
  destroyWorkspace: vi.fn(),
  startSession: vi.fn(),
  stopSession: vi.fn(),
  getTurns: vi.fn(),
  sendInput: vi.fn(),
}))

const mocked = {
  listWorkspaces: vi.mocked(listWorkspaces),
  getWorkspace: vi.mocked(getWorkspace),
  createWorkspace: vi.mocked(createWorkspace),
  destroyWorkspace: vi.mocked(destroyWorkspace),
  startSession: vi.mocked(startSession),
  getTurns: vi.mocked(getTurns),
}

function fakeWorkspace(over: Partial<Workspace> = {}): Workspace {
  return {
    id: '11111111-1111-1111-1111-111111111111',
    name: 'demo',
    repoUrl: null,
    branch: null,
    podName: null,
    gatewayEndpoint: null,
    status: 'READY',
    kind: 'REPO_BACKED',
    projectId: null,
    repositoryId: null,
    githubLinkId: null,
    createdAt: '2026-05-19T10:00:00Z',
    updatedAt: '2026-05-19T10:00:00Z',
    ...over,
  }
}

describe('useWorkspacesStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    Object.values(mocked).forEach((m) => m.mockReset())
  })

  it('loadAll populates workspaces', async () => {
    mocked.listWorkspaces.mockResolvedValue([fakeWorkspace()])
    const store = useWorkspacesStore()
    await store.loadAll()
    expect(store.workspaces).toHaveLength(1)
    expect(store.error).toBeNull()
  })

  it('open loads workspace + sessions and auto-selects first session turns', async () => {
    const detail: WorkspaceDetail = {
      workspace: fakeWorkspace(),
      sessions: [
        {
          id: 'sess-1',
          workspaceId: '11111111-1111-1111-1111-111111111111',
          kind: 'CLAUDE',
          gatewayAgentId: 'abc',
          status: 'RUNNING',
          createdAt: '2026-05-19T10:00:00Z',
          updatedAt: '2026-05-19T10:00:00Z',
        },
      ],
    }
    mocked.getWorkspace.mockResolvedValue(detail)
    mocked.getTurns.mockResolvedValue([])
    const store = useWorkspacesStore()
    await store.open('11111111-1111-1111-1111-111111111111')
    expect(store.sessions).toHaveLength(1)
    expect(store.activeSessionId).toBe('sess-1')
  })

  it('create unshifts the new workspace', async () => {
    const ws = fakeWorkspace({ id: 'new', name: 'fresh' })
    mocked.createWorkspace.mockResolvedValue(ws)
    const store = useWorkspacesStore()
    store.workspaces = [fakeWorkspace({ id: 'old' })]
    const result = await store.create({ name: 'fresh' })
    expect(result).toEqual(ws)
    expect(store.workspaces[0]!.id).toBe('new')
  })

  it('destroy removes workspace and clears active when matching', async () => {
    mocked.destroyWorkspace.mockResolvedValue()
    const store = useWorkspacesStore()
    const ws = fakeWorkspace({ id: 'a' })
    store.workspaces = [ws]
    store.activeWorkspace = ws
    await store.destroy('a')
    expect(store.workspaces).toHaveLength(0)
    expect(store.activeWorkspace).toBeNull()
  })

  it('appendStreamedOutput appends to the trailing streamed turn rather than creating new ones', () => {
    const store = useWorkspacesStore()
    store.activeSessionId = 'sess-1'
    store.appendStreamedOutput('hello ')
    store.appendStreamedOutput('world')
    expect(store.turns).toHaveLength(1)
    expect(store.turns[0]!.body).toBe('hello world')
    expect(store.turns[0]!.role).toBe('AGENT')
  })

  it('appendUserTurn adds a USER row immediately', () => {
    const store = useWorkspacesStore()
    store.activeSessionId = 'sess-1'
    store.appendUserTurn('do the thing')
    expect(store.turns).toHaveLength(1)
    expect(store.turns[0]!.role).toBe('USER')
  })

  it('newSession returns the new id and refreshes the workspace', async () => {
    mocked.startSession.mockResolvedValue({ sessionId: 'sess-2' })
    mocked.getWorkspace.mockResolvedValue({
      workspace: fakeWorkspace(),
      sessions: [],
    })
    const store = useWorkspacesStore()
    store.activeWorkspace = fakeWorkspace()
    const id = await store.newSession('CODEX')
    expect(id).toBe('sess-2')
    expect(mocked.startSession).toHaveBeenCalledWith(fakeWorkspace().id, 'CODEX', expect.any(Function))
  })
})
