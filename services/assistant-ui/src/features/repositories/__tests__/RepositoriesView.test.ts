import type { Repository } from '../types'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import RepositoriesView from '../views/RepositoriesView.vue'

const listRepositories = vi.fn<() => Promise<Repository[]>>()

vi.mock('../services/repositoriesService', () => ({
  listRepositories: () => listRepositories(),
  getRepository: vi.fn(),
  createRepository: vi.fn(),
  attachDeployKey: vi.fn(),
  deleteRepository: vi.fn(),
  verifyRepositoryAccess: vi.fn(),
}))

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/repositories', component: RepositoriesView },
    { path: '/repositories/:id', component: { template: '<div />' } },
  ],
})

describe('repositoriesView', () => {
  beforeEach(async () => {
    setActivePinia(createPinia())
    listRepositories.mockReset()
    await router.push('/repositories')
    await router.isReady()
  })

  it('places the GitHub App reference below the header and above loading state', async () => {
    listRepositories.mockReturnValue(new Promise(() => {}))

    const wrapper = mount(RepositoriesView, { global: { plugins: [router] } })
    await wrapper.vm.$nextTick()

    const header = wrapper.get('header')
    const reference = wrapper.get('[data-testid="github-app-reference"]')
    const loading = wrapper.findAll('div').find((node) => node.text().startsWith('Loading'))
    if (loading === undefined) throw new Error('missing loading state')

    expect(header.element.compareDocumentPosition(reference.element)).toBe(Node.DOCUMENT_POSITION_FOLLOWING)
    expect(reference.element.compareDocumentPosition(loading.element)).toBe(Node.DOCUMENT_POSITION_FOLLOWING)
    expect(reference.get('[data-testid="github-app-public-link"]').attributes('href')).toBe(
      'https://github.com/apps/personal-stack-agents',
    )
  })
})
