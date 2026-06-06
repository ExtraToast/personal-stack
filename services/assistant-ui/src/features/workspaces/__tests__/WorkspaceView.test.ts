import type { AgentSession, WorkspaceDetail, WorkspaceRepository } from '../types'
import type { Repository } from '@/features/repositories'
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
  setReconnect: vi.fn(),
  reconnectNow: vi.fn(),
  close: vi.fn(),
  readyState: vi.fn(() => 1),
}
const attachSessionSocket = vi.fn(() => socket)
vi.mock('../services/sessionSocket', () => ({
  attachSessionSocket: () => attachSessionSocket(),
}))

const getWorkspace = vi.fn<(id: string) => Promise<WorkspaceDetail>>()
const attachRepository = vi.fn()
const detachRepository = vi.fn()
const sendInput = vi.fn()
const stageInput = vi.fn()
vi.mock('../services/workspaceService', () => ({
  listWorkspaces: vi.fn(),
  getWorkspace: (id: string) => getWorkspace(id),
  createWorkspace: vi.fn(),
  destroyWorkspace: vi.fn(),
  startSession: vi.fn(),
  stopSession: vi.fn(),
  attachRepository: (...args: unknown[]) => attachRepository(...args),
  detachRepository: (...args: unknown[]) => detachRepository(...args),
  getTurns: vi.fn(async () => []),
  sendInput: (...args: unknown[]) => sendInput(...args),
  stageInput: (...args: unknown[]) => stageInput(...args),
}))

const listRepositories = vi.fn<() => Promise<Repository[]>>()
vi.mock('@/features/repositories/services/repositoriesService', () => ({
  listRepositories: () => listRepositories(),
  getRepository: vi.fn(),
  createRepository: vi.fn(),
  attachDeployKey: vi.fn(),
  deleteRepository: vi.fn(),
  verifyRepositoryAccess: vi.fn(),
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

function fakeRepository(over: Partial<Repository> = {}): Repository {
  return {
    id: 'repo-primary',
    name: 'primary',
    repoUrl: 'git@github.com:owner/primary.git',
    defaultBranch: 'main',
    vaultKeyPath: 'secret/data/agents/repositories/repo-primary',
    deployKeyFingerprint: 'SHA256:primary',
    deployKeyAddedAt: '2026-05-19T10:00:00Z',
    createdAt: '2026-05-19T10:00:00Z',
    updatedAt: '2026-05-19T10:00:00Z',
    ...over,
  }
}

function fakeWorkspaceRepository(over: Partial<WorkspaceRepository> = {}): WorkspaceRepository {
  const repo = fakeRepository(over)
  return {
    ...repo,
    verification: null,
    isPrimary: false,
    attachedAt: '2026-05-20T10:00:00Z',
    ...over,
  }
}

function detail(sessions: AgentSession[], workspace: Partial<WorkspaceDetail['workspace']> = {}): WorkspaceDetail {
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
      repositories: [],
      ...workspace,
    },
    sessions,
  }
}

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/sessions', component: { template: '<div />' } },
    { path: '/repositories', component: { template: '<div />' } },
    { path: '/repositories/:id', component: { template: '<div />' } },
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

function requireElement<T extends Element>(selector: string): T {
  const el = document.querySelector<T>(selector)
  if (!el) throw new Error(`missing element: ${selector}`)
  return el
}

describe('workspaceView terminal persistence', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    Object.values(term).forEach((m) => m.mockClear())
    Object.values(socket).forEach((m) => m.mockClear())
    attachSessionSocket.mockClear()
    getWorkspace.mockReset()
    attachRepository.mockReset()
    detachRepository.mockReset()
    listRepositories.mockReset()
    listRepositories.mockResolvedValue([])
    sendInput.mockReset()
    stageInput.mockReset()
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

  it('stages large text then sends only a pointer prompt to the active session', async () => {
    getWorkspace.mockResolvedValue(detail([fakeSession({ id: 'sess-a', gatewayAgentId: 'abc12345' })]))
    stageInput.mockResolvedValue({
      path: '/workspace/.agent-inputs/20260604-source.txt',
      bytes: 14,
      name: 'source.txt',
    })
    const wrapper = await mountView()

    await wrapper.find('[data-testid="stage-input-open"]').trigger('click')
    await flush()
    const name = requireElement<HTMLInputElement>('[data-testid="stage-input-name"]')
    const content = requireElement<HTMLTextAreaElement>('[data-testid="stage-input-content"]')
    const submit = requireElement<HTMLButtonElement>('[data-testid="stage-input-submit"]')
    name.value = 'source.txt'
    name.dispatchEvent(new Event('input'))
    content.value = 'large document'
    content.dispatchEvent(new Event('input'))
    await wrapper.vm.$nextTick()
    submit.click()
    await flush()

    expect(stageInput).toHaveBeenCalledWith('ws-1', 'sess-a', 'large document', 'source.txt')
    expect(sendInput).toHaveBeenCalledWith(
      'ws-1',
      'sess-a',
      'Please read /workspace/.agent-inputs/20260604-source.txt and use it as the source document for the next task.',
      true,
    )
    expect(sendInput.mock.calls[0]?.[2]).not.toContain('large document')
  })

  it('renders attached repositories, marks the primary, and shows split guidance', async () => {
    const primary = fakeWorkspaceRepository({ id: 'repo-primary', name: 'primary', isPrimary: true })
    const destination = fakeWorkspaceRepository({
      id: 'repo-dest',
      name: 'split-dest',
      repoUrl: 'git@github.com:owner/split-dest.git',
    })
    getWorkspace.mockResolvedValue(
      detail([fakeSession({ id: 'sess-a' })], { projectId: 'project-1', repositories: [primary, destination] }),
    )

    const wrapper = await mountView()

    expect(wrapper.find('[data-testid="workspace-repositories-panel"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="workspace-repository-primary-repo-primary"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="workspace-detach-repository-repo-primary"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="workspace-detach-repository-repo-dest"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="workspace-split-command"]').text()).toBe(
      'council split --path path/to/subtree --dest owner/split-dest',
    )
    expect(wrapper.find('[data-testid="split-follow-up"]').text()).toContain(
      'Keep owner/split-dest linked in the project repository pool',
    )
    expect(wrapper.find('[data-testid="split-follow-up"]').text()).toContain(
      'Start the next runner from owner/split-dest after the split lands.',
    )
  })

  it('shows non-project split follow-up wording', async () => {
    const primary = fakeWorkspaceRepository({ id: 'repo-primary', name: 'primary', isPrimary: true })
    const destination = fakeWorkspaceRepository({
      id: 'repo-dest',
      name: 'split-dest',
      repoUrl: 'git@github.com:owner/split-dest.git',
    })
    getWorkspace.mockResolvedValue(detail([fakeSession({ id: 'sess-a' })], { repositories: [primary, destination] }))

    const wrapper = await mountView()

    expect(wrapper.find('[data-testid="split-follow-up"]').text()).toContain(
      'Keep owner/split-dest attached here, or open a new workspace from that repository.',
    )
  })

  it('loads candidate repositories, filters attached repositories, and attaches the selected one', async () => {
    const primary = fakeWorkspaceRepository({ id: 'repo-primary', name: 'primary', isPrimary: true })
    const extra = fakeWorkspaceRepository({
      id: 'repo-extra',
      name: 'extra',
      repoUrl: 'git@github.com:owner/extra.git',
    })
    getWorkspace
      .mockResolvedValueOnce(detail([fakeSession({ id: 'sess-a' })], { repositories: [primary] }))
      .mockResolvedValueOnce(detail([fakeSession({ id: 'sess-a' })], { repositories: [primary, extra] }))
    listRepositories.mockResolvedValue([
      fakeRepository({ id: 'repo-primary', name: 'primary' }),
      fakeRepository({ id: 'repo-extra', name: 'extra', repoUrl: 'git@github.com:owner/extra.git' }),
    ])
    attachRepository.mockResolvedValue([extra])
    const wrapper = await mountView()

    await wrapper.find('[data-testid="workspace-add-repository"]').trigger('click')
    await flush()

    expect(listRepositories).toHaveBeenCalledOnce()
    expect(document.querySelector('[data-testid="repository-picker-radio-repo-primary"]')).toBeNull()
    const extraRadio = requireElement<HTMLInputElement>('[data-testid="repository-picker-radio-repo-extra"]')
    extraRadio.click()
    await wrapper.vm.$nextTick()
    requireElement<HTMLButtonElement>('[data-testid="repository-picker-submit"]').click()
    await flush()

    expect(attachRepository).toHaveBeenCalledWith('ws-1', 'repo-extra')
    expect(wrapper.find('[data-testid="workspace-repository-repo-extra"]').exists()).toBe(true)
  })

  it('removes non-primary repositories and reports detach failures', async () => {
    const primary = fakeWorkspaceRepository({ id: 'repo-primary', name: 'primary', isPrimary: true })
    const destination = fakeWorkspaceRepository({
      id: 'repo-dest',
      name: 'split-dest',
      repoUrl: 'git@github.com:owner/split-dest.git',
    })
    getWorkspace.mockResolvedValue(detail([fakeSession({ id: 'sess-a' })], { repositories: [primary, destination] }))
    detachRepository.mockRejectedValue(new Error('detach failed'))
    const wrapper = await mountView()

    await wrapper.find('[data-testid="workspace-detach-repository-repo-dest"]').trigger('click')
    await flush()

    expect(detachRepository).toHaveBeenCalledWith('ws-1', 'repo-dest')
    expect(wrapper.find('[data-testid="workspace-repositories-panel"]').text()).toContain(
      'Could not remove the repository',
    )
  })
})

async function flush(): Promise<void> {
  await Promise.resolve()
  await Promise.resolve()
  await Promise.resolve()
}
