import type { AgentSession, WorkspaceDetail } from '../types'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { useWorkspacesStore } from '../stores/workspaces'
import WorkspaceView from '../views/WorkspaceView.vue'

// xterm + the WebSocket wrapper touch real DOM/network the same way
// SessionTerminal.test.ts stubs them. Reuse the same fakes here so the
// view mounts real SessionTerminal children and the mount/dispose
// lifecycle can be asserted across tab switches.
const term = {
  write: vi.fn(),
  loadAddon: vi.fn(),
  open: vi.fn(),
  onData: vi.fn(),
  onResize: vi.fn(),
  focus: vi.fn(),
  dispose: vi.fn(),
}
vi.mock('@xterm/xterm', () => ({
  Terminal: class {
    write = term.write
    loadAddon = term.loadAddon
    open = term.open
    onData = term.onData
    onResize = term.onResize
    focus = term.focus
    dispose = term.dispose
  },
}))
vi.mock('@xterm/xterm/css/xterm.css', () => ({}))
vi.mock('@xterm/addon-fit', () => ({
  FitAddon: class {
    fit = vi.fn()
  },
}))

const socket = {
  send: vi.fn(),
  sendKey: vi.fn(),
  sendResize: vi.fn(),
  close: vi.fn(),
  readyState: vi.fn(() => 1),
}
const attachSessionSocket = vi.fn(() => socket)
vi.mock('../services/sessionSocket', () => ({
  attachSessionSocket: () => attachSessionSocket(),
}))

const getWorkspace = vi.fn<(id: string) => Promise<WorkspaceDetail>>()
vi.mock('../services/workspaceService', () => ({
  listWorkspaces: vi.fn(),
  getWorkspace: (id: string) => getWorkspace(id),
  createWorkspace: vi.fn(),
  destroyWorkspace: vi.fn(),
  startSession: vi.fn(),
  stopSession: vi.fn(),
  getTurns: vi.fn(async () => []),
  sendInput: vi.fn(),
}))

function fakeSession(over: Partial<AgentSession> = {}): AgentSession {
  return {
    id: 'sess-a',
    workspaceId: 'ws-1',
    kind: 'CLAUDE',
    gatewayAgentId: null,
    status: 'RUNNING',
    createdAt: '2026-05-19T10:00:00Z',
    updatedAt: '2026-05-19T10:00:00Z',
    ...over,
  }
}

function detail(sessions: AgentSession[]): WorkspaceDetail {
  return {
    workspace: {
      id: 'ws-1',
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
    },
    sessions,
  }
}

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/sessions', component: { template: '<div />' } },
    { path: '/workspaces/:id', component: WorkspaceView },
  ],
})

async function mountView() {
  await router.push('/workspaces/ws-1')
  await router.isReady()
  const wrapper = mount(WorkspaceView, {
    global: {
      plugins: [router],
      stubs: { AgentKindPicker: true, SessionTabs: true },
    },
  })
  await flush()
  return wrapper
}

describe('workspaceView terminal persistence', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    Object.values(term).forEach((m) => m.mockClear())
    Object.values(socket).forEach((m) => m.mockClear())
    attachSessionSocket.mockClear()
    getWorkspace.mockReset()
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe() {}
        disconnect() {}
      },
    )
  })

  it('keeps every live session terminal mounted, one socket per session', async () => {
    getWorkspace.mockResolvedValue(detail([fakeSession({ id: 'sess-a' }), fakeSession({ id: 'sess-b' })]))
    const wrapper = await mountView()

    expect(attachSessionSocket).toHaveBeenCalledTimes(2)
    expect(wrapper.findAll('[data-testid="session-terminal"]').length).toBe(2)
  })

  it('switching the active session does not dispose the previous terminal or close its socket', async () => {
    getWorkspace.mockResolvedValue(detail([fakeSession({ id: 'sess-a' }), fakeSession({ id: 'sess-b' })]))
    const wrapper = await mountView()
    const store = useWorkspacesStore()

    store.activeSessionId = 'sess-a'
    await wrapper.vm.$nextTick()
    store.activeSessionId = 'sess-b'
    await wrapper.vm.$nextTick()

    expect(socket.close).not.toHaveBeenCalled()
    expect(term.dispose).not.toHaveBeenCalled()
    expect(attachSessionSocket).toHaveBeenCalledTimes(2)
  })

  it('drops a session that stopped, disposing its terminal and closing its socket', async () => {
    getWorkspace.mockResolvedValue(detail([fakeSession({ id: 'sess-a' }), fakeSession({ id: 'sess-b' })]))
    const wrapper = await mountView()
    const store = useWorkspacesStore()
    expect(attachSessionSocket).toHaveBeenCalledTimes(2)

    store.sessions = [fakeSession({ id: 'sess-a' }), fakeSession({ id: 'sess-b', status: 'STOPPED' })]
    await wrapper.vm.$nextTick()

    expect(wrapper.findAll('[data-testid="session-terminal"]').length).toBe(1)
    expect(socket.close).toHaveBeenCalledTimes(1)
    expect(term.dispose).toHaveBeenCalledTimes(1)
  })
})

async function flush(): Promise<void> {
  await Promise.resolve()
  await Promise.resolve()
  await Promise.resolve()
}
