import type { Workspace } from '@/features/workspaces/types'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { useWorkspacesStore } from '@/features/workspaces'
import WorkspaceTab from '../components/WorkspaceTab.vue'

function repoBackedWorkspace(id: string, name: string): Workspace {
  const now = new Date().toISOString()
  return {
    id,
    name,
    repoUrl: 'git@github.com:ExtraToast/demo.git',
    branch: 'main',
    podName: null,
    gatewayEndpoint: null,
    status: 'READY',
    kind: 'REPO_BACKED',
    projectId: null,
    repositoryId: 'r1',
    githubLinkId: null,
    createdAt: now,
    updatedAt: now,
  }
}

function mountTab() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/:pathMatch(.*)*', component: { template: '<div />' } }],
  })
  return mount(WorkspaceTab, {
    global: {
      plugins: [router],
      stubs: { CreateWorkspaceWizard: true },
    },
  })
}

describe('workspace tab delete', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('deletes a workspace through the store after the user confirms', async () => {
    const store = useWorkspacesStore()
    store.loadAll = vi.fn().mockResolvedValue(undefined)
    store.destroy = vi.fn().mockResolvedValue(undefined)
    store.workspaces = [repoBackedWorkspace('w1', 'demo')]
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    const wrapper = mountTab()
    await wrapper.get('[data-testid="workspace-delete-w1"]').trigger('click')

    expect(window.confirm).toHaveBeenCalledOnce()
    expect(store.destroy).toHaveBeenCalledWith('w1')
  })

  it('does not delete when the user cancels the confirm', async () => {
    const store = useWorkspacesStore()
    store.loadAll = vi.fn().mockResolvedValue(undefined)
    store.destroy = vi.fn().mockResolvedValue(undefined)
    store.workspaces = [repoBackedWorkspace('w1', 'demo')]
    vi.spyOn(window, 'confirm').mockReturnValue(false)

    const wrapper = mountTab()
    await wrapper.get('[data-testid="workspace-delete-w1"]').trigger('click')

    expect(store.destroy).not.toHaveBeenCalled()
  })
})
