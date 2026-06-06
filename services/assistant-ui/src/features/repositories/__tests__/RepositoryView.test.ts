import type { Repository, RepositoryDetail, RepositoryVerifyResult } from '../types'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import RepositoryView from '../views/RepositoryView.vue'

const getRepository = vi.fn<(id: string) => Promise<RepositoryDetail>>()
const verifyRepositoryAccess = vi.fn<(id: string) => Promise<RepositoryVerifyResult>>()

vi.mock('../services/repositoriesService', () => ({
  listRepositories: vi.fn(),
  getRepository: (id: string) => getRepository(id),
  createRepository: vi.fn(),
  attachDeployKey: vi.fn(),
  deleteRepository: vi.fn(),
  verifyRepositoryAccess: (id: string) => verifyRepositoryAccess(id),
}))

function fakeRepo(over: Partial<Repository> = {}): Repository {
  return {
    id: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    name: 'demo',
    repoUrl: 'git@github.com:owner/demo.git',
    defaultBranch: 'main',
    vaultKeyPath: 'secret/data/agents/repositories/aaaa',
    deployKeyFingerprint: 'SHA256:abc',
    deployKeyAddedAt: '2026-05-20T10:00:00Z',
    createdAt: '2026-05-20T10:00:00Z',
    updatedAt: '2026-05-20T10:00:00Z',
    ...over,
  }
}

function detail(over: Partial<RepositoryDetail> = {}): RepositoryDetail {
  return { repository: fakeRepo(), attachedProjects: [], ...over }
}

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/repositories', component: { template: '<div />' } },
    { path: '/repositories/:id', component: RepositoryView },
    { path: '/projects/:id', component: { template: '<div />' } },
  ],
})

async function mountView() {
  await router.push('/repositories/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa')
  await router.isReady()
  const wrapper = mount(RepositoryView, { global: { plugins: [router] } })
  await flush()
  return wrapper
}

describe('repositoryView access status', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    getRepository.mockReset()
    verifyRepositoryAccess.mockReset()
  })

  it('shows "not verified" until a verify result is present', async () => {
    getRepository.mockResolvedValue(detail())
    const wrapper = await mountView()
    expect(wrapper.find('[data-testid="access-status-unverified"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="access-status-badge"]').exists()).toBe(false)
  })

  it('renders read/write/protection badges with the right tones once verified', async () => {
    getRepository.mockResolvedValue(
      detail({
        verify: {
          read: true,
          write: false,
          defaultBranchProtected: false,
          checkedAt: '2026-05-21T10:00:00Z',
          messages: ['probe ref deleted'],
        },
      }),
    )
    const wrapper = await mountView()

    expect(wrapper.find('[data-testid="access-read"]').attributes('data-tone')).toBe('ok')
    expect(wrapper.find('[data-testid="access-write"]').attributes('data-tone')).toBe('warn')
    expect(wrapper.find('[data-testid="access-write"]').text()).toContain('Read-only')
    expect(wrapper.find('[data-testid="access-protection"]').attributes('data-tone')).toBe('warn')
    // Unprotected default branch surfaces the history-loss warning.
    expect(wrapper.find('[data-testid="repository-protection-warning"]').exists()).toBe(true)
  })

  it('renders protection as unknown when the token is absent (null)', async () => {
    getRepository.mockResolvedValue(
      detail({
        verify: {
          read: true,
          write: true,
          defaultBranchProtected: null,
          checkedAt: '2026-05-21T10:00:00Z',
          messages: [],
        },
      }),
    )
    const wrapper = await mountView()
    expect(wrapper.find('[data-testid="access-protection"]').attributes('data-tone')).toBe('unknown')
    expect(wrapper.find('[data-testid="repository-protection-unknown"]').exists()).toBe(true)
  })

  it('renders GitHub App owner guidance and canonical external links', async () => {
    getRepository.mockResolvedValue(detail({ repository: fakeRepo({ repoUrl: 'git@github.com:ExtraToast/demo.git' }) }))
    const wrapper = await mountView()

    expect(wrapper.find('[data-testid="github-app-owner"]').text()).toBe('ExtraToast')
    expect(wrapper.find('[data-testid="github-app-permissions"]').text()).toMatch(/Contents:\s+write/)
    expect(wrapper.find('[data-testid="github-app-permissions"]').text()).toMatch(/Pull requests:\s+write/)
    expect(wrapper.find('[data-testid="github-app-permissions"]').text()).toMatch(/Actions:\s+write/)
    expect(wrapper.find('[data-testid="github-app-approval-note"]').text()).toContain('approval on each installation')

    const expectedLinks = {
      'github-app-install-link': 'https://github.com/apps/personal-stack-agents/installations/new?state=ExtraToast',
      'github-app-user-installations-link': 'https://github.com/settings/installations',
      'github-app-organization-installations-link':
        'https://github.com/organizations/ExtraToast/settings/installations',
      'github-app-permissions-link': 'https://github.com/settings/apps/personal-stack-agents/permissions',
    }

    for (const [testId, href] of Object.entries(expectedLinks)) {
      const link = wrapper.find(`[data-testid="${testId}"]`)
      expect(link.attributes('href')).toBe(href)
      expect(link.attributes('target')).toBe('_blank')
      expect(link.attributes('rel')).toBe('noopener noreferrer')
    }
  })

  it('verify-access button calls the verify endpoint and stores the result', async () => {
    getRepository.mockResolvedValue(detail())
    verifyRepositoryAccess.mockResolvedValue({
      read: true,
      write: true,
      defaultBranchProtected: true,
      checkedAt: '2026-05-21T10:00:00Z',
      messages: [],
    })
    const wrapper = await mountView()

    await wrapper.find('[data-testid="repository-verify"]').trigger('click')
    await flush()

    expect(verifyRepositoryAccess).toHaveBeenCalledOnce()
    expect(wrapper.find('[data-testid="access-read"]').attributes('data-tone')).toBe('ok')
    expect(wrapper.find('[data-testid="access-protection"]').text()).toContain('Branch protected')
  })

  it('replace-key opens the wizard in replace mode and auto-verifies after success', async () => {
    getRepository.mockResolvedValue(detail())
    verifyRepositoryAccess.mockResolvedValue({
      read: true,
      write: false,
      defaultBranchProtected: true,
      checkedAt: '2026-05-21T10:00:00Z',
      messages: [],
    })
    const wrapper = await mountView()

    await wrapper.find('[data-testid="repository-replace-key"]').trigger('click')
    await flush()

    // The Modal teleports its content to <body>, so query the wizard
    // component directly rather than through the view wrapper.
    const wizard = wrapper.findComponent({ name: 'AttachKeyWizard' })
    expect(wizard.exists()).toBe(true)
    // Replace mode is reflected in the wizard's prop + rotation notice.
    expect(wizard.props('replacing')).toBe(true)
    expect(wizard.find('[data-testid="attach-key-replace-notice"]').exists()).toBe(true)
    // The branch-protection guidance is always present in the wizard.
    expect(wizard.find('[data-testid="attach-key-protection-notice"]').exists()).toBe(true)

    // Simulate the wizard reporting a successful key write.
    await wizard.vm.$emit('success')
    await flush()

    expect(verifyRepositoryAccess).toHaveBeenCalledOnce()
  })
})

async function flush(): Promise<void> {
  await Promise.resolve()
  await Promise.resolve()
  await Promise.resolve()
}
