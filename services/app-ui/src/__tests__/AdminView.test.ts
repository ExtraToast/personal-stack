import type { AdminUser } from '../features/admin/services/adminService'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AdminView from '../features/admin/views/AdminView.vue'
import { useAuthStore } from '../features/auth'

const mockReplace = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({ replace: mockReplace }),
}))

vi.mock('../features/admin/services/adminService', () => ({
  fetchUsers: vi.fn(),
  ALL_ROLES: ['ADMIN', 'USER', 'READONLY'],
  ALL_SERVICES: ['GRAFANA', 'VAULT'],
}))

function makeJwt(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: 'RS256', typ: 'JWT' }))
  const body = btoa(JSON.stringify(payload))
  return `${header}.${body}.signature`
}

const mockUsers: AdminUser[] = [
  {
    id: 'u1',
    username: 'alice',
    email: 'alice@example.com',
    role: 'USER',
    emailConfirmed: true,
    totpEnabled: false,
    servicePermissions: ['GRAFANA'],
    createdAt: '2025-01-01T00:00:00Z',
  },
]

describe('adminView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('renders user table when admin', async () => {
    const { fetchUsers } = await import('../features/admin/services/adminService')
    vi.mocked(fetchUsers).mockResolvedValue(mockUsers)

    const token = makeJwt({ sub: 'a1', username: 'admin', roles: ['ROLE_ADMIN'] })
    const authStore = useAuthStore()
    authStore.accessToken = token
    authStore.roles = ['ROLE_ADMIN']

    const wrapper = mount(AdminView, {
      global: {
        stubs: {
          UserTable: { template: '<div class="user-table-stub" />' },
        },
      },
    })
    await flushPromises()

    expect(wrapper.find('.user-table-stub').exists()).toBe(true)
    expect(mockReplace).not.toHaveBeenCalled()
  })

  it('redirects non-admin users to home', async () => {
    const authStore = useAuthStore()
    authStore.roles = ['ROLE_USER']

    mount(AdminView, {
      global: {
        stubs: {
          UserTable: { template: '<div />' },
        },
      },
    })
    await flushPromises()

    expect(mockReplace).toHaveBeenCalledWith('/')
  })

  it('fetches users on mount', async () => {
    const { fetchUsers } = await import('../features/admin/services/adminService')
    vi.mocked(fetchUsers).mockResolvedValue(mockUsers)

    const token = makeJwt({ sub: 'a1', username: 'admin', roles: ['ROLE_ADMIN'] })
    const authStore = useAuthStore()
    authStore.accessToken = token
    authStore.roles = ['ROLE_ADMIN']

    mount(AdminView, {
      global: {
        stubs: {
          UserTable: { template: '<div />' },
        },
      },
    })
    await flushPromises()

    expect(fetchUsers).toHaveBeenCalledWith(token)
  })

  it('shows loading state', async () => {
    const { fetchUsers } = await import('../features/admin/services/adminService')
    vi.mocked(fetchUsers).mockReturnValue(new Promise(() => {}))

    const token = makeJwt({ sub: 'a1', username: 'admin', roles: ['ROLE_ADMIN'] })
    const authStore = useAuthStore()
    authStore.accessToken = token
    authStore.roles = ['ROLE_ADMIN']

    const wrapper = mount(AdminView, {
      global: {
        stubs: {
          UserTable: { template: '<div />' },
        },
      },
    })

    expect(wrapper.text()).toContain('Loading')
  })

  it('shows error state on fetch failure', async () => {
    const { fetchUsers } = await import('../features/admin/services/adminService')
    vi.mocked(fetchUsers).mockRejectedValue(new Error('Network error'))

    const token = makeJwt({ sub: 'a1', username: 'admin', roles: ['ROLE_ADMIN'] })
    const authStore = useAuthStore()
    authStore.accessToken = token
    authStore.roles = ['ROLE_ADMIN']

    const wrapper = mount(AdminView, {
      global: {
        stubs: {
          UserTable: { template: '<div />' },
        },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('Failed to load users')
  })
})
