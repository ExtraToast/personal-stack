import type { Project, ProjectDetail } from '@/features/projects'
import type { Repository, RepositoryDetail } from '@/features/repositories'
import type { Workspace } from '@/features/workspaces'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import CreateWorkspaceWizard from '../components/CreateWorkspaceWizard.vue'

vi.mock('@/lib/vueWebCommons', () => ({
  FormErrors: {
    props: ['error'],
    template: '<div v-if="error">{{ error }}</div>',
  },
  FormField: {
    props: ['label'],
    template: '<label><span>{{ label }}</span><slot :id="label" /></label>',
  },
  SubmitButton: {
    props: ['disabled', 'label', 'type'],
    emits: ['click'],
    template:
      '<button v-bind="$attrs" :type="type || \'button\'" :disabled="disabled" @click="$emit(\'click\')">{{ label }}</button>',
  },
  useFormErrors: () => ({
    general: { value: null },
    clear: vi.fn(),
    captureFromCatch: vi.fn(),
    fieldErrorFor: vi.fn(() => null),
  }),
  useMutationState: () => ({
    status: { value: 'idle' },
    run: async (fn: () => Promise<void>) => fn(),
  }),
  useToast: () => ({
    success: vi.fn(),
    errorFromCatch: vi.fn(),
  }),
}))

const listProjects = vi.fn<() => Promise<Project[]>>()
const getProject = vi.fn<(id: string) => Promise<ProjectDetail>>()
vi.mock('@/features/projects/services/projectsService', () => ({
  listProjects: () => listProjects(),
  getProject: (id: string) => getProject(id),
  createProject: vi.fn(),
  linkRepository: vi.fn(),
  unlinkRepository: vi.fn(),
  addLink: vi.fn(),
  removeLink: vi.fn(),
  attachKey: vi.fn(),
}))

const getRepository = vi.fn<(id: string) => Promise<RepositoryDetail>>()
vi.mock('@/features/repositories/services/repositoriesService', () => ({
  listRepositories: vi.fn(),
  getRepository: (id: string) => getRepository(id),
  createRepository: vi.fn(),
  attachDeployKey: vi.fn(),
  deleteRepository: vi.fn(),
  verifyRepositoryAccess: vi.fn(),
}))

const createWorkspace = vi.fn()
const attachRepository = vi.fn()
vi.mock('@/features/workspaces/services/workspaceService', () => ({
  listWorkspaces: vi.fn(),
  getWorkspace: vi.fn(),
  createWorkspace: (...args: unknown[]) => createWorkspace(...args),
  destroyWorkspace: vi.fn(),
  attachRepository: (...args: unknown[]) => attachRepository(...args),
  detachRepository: vi.fn(),
  startSession: vi.fn(),
  stopSession: vi.fn(),
  getTurns: vi.fn(),
  sendInput: vi.fn(),
}))

function fakeProject(over: Partial<Project> = {}): Project {
  return {
    id: 'project-1',
    name: 'Project One',
    slug: 'project-one',
    description: '',
    createdAt: '2026-05-20T10:00:00Z',
    updatedAt: '2026-05-20T10:00:00Z',
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
    deployKeyAddedAt: '2026-05-20T10:00:00Z',
    createdAt: '2026-05-20T10:00:00Z',
    updatedAt: '2026-05-20T10:00:00Z',
    ...over,
  }
}

function fakeWorkspace(over: Partial<Workspace> = {}): Workspace {
  return {
    id: 'ws-new',
    name: 'workspace',
    repoUrl: 'git@github.com:owner/primary.git',
    branch: 'trunk',
    podName: null,
    gatewayEndpoint: null,
    status: 'PENDING',
    kind: 'REPO_BACKED',
    projectId: 'project-1',
    repositoryId: 'repo-primary',
    githubLinkId: null,
    createdAt: '2026-05-20T10:00:00Z',
    updatedAt: '2026-05-20T10:00:00Z',
    ...over,
  }
}

async function mountWizard() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/sessions', component: { template: '<div />' } },
      { path: '/sessions/workspace/:id', component: { template: '<div />' } },
      { path: '/projects', component: { template: '<div />' } },
      { path: '/repositories', component: { template: '<div />' } },
      { path: '/repositories/:id', component: { template: '<div />' } },
    ],
  })
  await router.push('/sessions')
  await router.isReady()

  const wrapper = mount(CreateWorkspaceWizard, {
    props: { open: true },
    global: { plugins: [router] },
  })
  await flush()
  return { router, wrapper }
}

describe('createWorkspaceWizard', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    listProjects.mockReset()
    getProject.mockReset()
    getRepository.mockReset()
    createWorkspace.mockReset()
    attachRepository.mockReset()
  })

  it('creates with a primary repository and selected repository ids', async () => {
    const project = fakeProject()
    const primary = fakeRepository({ defaultBranch: 'trunk' })
    const extra = fakeRepository({
      id: 'repo-extra',
      name: 'extra',
      repoUrl: 'git@github.com:owner/extra.git',
      vaultKeyPath: 'secret/data/agents/repositories/repo-extra',
      deployKeyFingerprint: 'SHA256:extra',
    })
    const skipped = fakeRepository({
      id: 'repo-skipped',
      name: 'skipped',
      repoUrl: 'git@github.com:owner/skipped.git',
      vaultKeyPath: 'secret/data/agents/repositories/repo-skipped',
      deployKeyFingerprint: 'SHA256:skipped',
    })
    listProjects.mockResolvedValue([project])
    getProject.mockResolvedValue({ project, links: [], repositories: [primary, extra, skipped] })
    getRepository.mockResolvedValue({ repository: primary, attachedProjects: [] })
    createWorkspace.mockResolvedValue(fakeWorkspace())

    const { router, wrapper } = await mountWizard()

    await wrapper.get('[data-testid="wizard-project-project-1"]').setValue()
    await flush()
    await wrapper.get('[data-testid="wizard-step1-next"]').trigger('click')
    await flush()
    await wrapper.get('[data-testid="wizard-repo-primary-repo-primary"]').setValue()
    await flush()
    await wrapper.get('[data-testid="wizard-repo-checkbox-repo-extra"]').setValue(true)
    await flush()
    await wrapper.get('[data-testid="wizard-step2-next"]').trigger('click')
    await flush()
    await wrapper.get('[data-testid="wizard-name"]').setValue('workspace')
    await wrapper.get('[data-testid="wizard-submit"]').trigger('click')
    await flush()

    expect(createWorkspace).toHaveBeenCalledWith({
      name: 'workspace',
      kind: 'REPO_BACKED',
      projectId: 'project-1',
      repositoryId: 'repo-primary',
      primaryRepositoryId: 'repo-primary',
      repositoryIds: ['repo-primary', 'repo-extra'],
      branch: 'trunk',
    })
    expect(attachRepository).not.toHaveBeenCalled()
    await vi.waitFor(() => {
      expect(router.currentRoute.value.fullPath).toBe('/sessions/workspace/ws-new')
    })
  })
})

async function flush(): Promise<void> {
  await Promise.resolve()
  await Promise.resolve()
  await Promise.resolve()
}
