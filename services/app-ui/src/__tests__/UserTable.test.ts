import type { AdminUser } from '../features/admin/services/adminService'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import UserTable from '../features/admin/components/UserTable.vue'
import { useAuthStore } from '../features/auth'

const mockUser: AdminUser = {
  id: 'u1',
  username: 'alice',
  email: 'alice@example.com',
  role: 'USER',
  emailConfirmed: true,
  totpEnabled: false,
  servicePermissions: ['GRAFANA'],
  createdAt: '2025-01-01T00:00:00Z',
}

describe('userTable', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('renders user rows with username and email', () => {
    const authStore = useAuthStore()
    authStore.accessToken = 'token'

    const wrapper = mount(UserTable, { props: { users: [mockUser] } })
    expect(wrapper.text()).toContain('alice')
    expect(wrapper.text()).toContain('alice@example.com')
  })

  it('opens confirmation dialog when delete is clicked', async () => {
    const authStore = useAuthStore()
    authStore.accessToken = 'token'

    const wrapper = mount(UserTable, { props: { users: [mockUser] } })
    const deleteBtn = wrapper.findAll('button').find((b) => b.text() === 'Delete')
    await deleteBtn?.trigger('click')

    expect(wrapper.text()).toContain('This cannot be undone')
  })

  it('closes dialog on cancel', async () => {
    const authStore = useAuthStore()
    authStore.accessToken = 'token'

    const wrapper = mount(UserTable, { props: { users: [mockUser] } })
    const deleteBtn = wrapper.findAll('button').find((b) => b.text() === 'Delete')
    await deleteBtn?.trigger('click')

    const cancelBtn = wrapper.findAll('button').find((b) => b.text() === 'Cancel')
    await cancelBtn?.trigger('click')

    expect(wrapper.text()).not.toContain('This cannot be undone')
  })

  it('renders multiple users', () => {
    const second: AdminUser = { ...mockUser, id: 'u2', username: 'bob', email: 'bob@example.com' }
    const authStore = useAuthStore()
    authStore.accessToken = 'token'

    const wrapper = mount(UserTable, { props: { users: [mockUser, second] } })
    expect(wrapper.text()).toContain('alice')
    expect(wrapper.text()).toContain('bob')
  })

  it('role selector triggers onRoleChange callback', async () => {
    const authStore = useAuthStore()
    authStore.accessToken = 'token'

    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({ ...mockUser, role: 'ADMIN' }),
      }),
    )

    const wrapper = mount(UserTable, { props: { users: [mockUser] } })
    const select = wrapper.find('select')
    await select.setValue('ADMIN')

    expect(fetch).toHaveBeenCalled()
  })

  it('service permissions editor renders for each user', () => {
    const second: AdminUser = {
      ...mockUser,
      id: 'u2',
      username: 'bob',
      email: 'bob@example.com',
      servicePermissions: ['VAULT'],
    }
    const authStore = useAuthStore()
    authStore.accessToken = 'token'

    const wrapper = mount(UserTable, { props: { users: [mockUser, second] } })
    const serviceEditors = wrapper.findAllComponents({ name: 'ServicePermissionsEditor' })
    expect(serviceEditors).toHaveLength(2)
  })

  it('delete confirmation triggers onDelete callback', async () => {
    const authStore = useAuthStore()
    authStore.accessToken = 'token'

    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve(null),
      }),
    )

    const wrapper = mount(UserTable, { props: { users: [mockUser] } })

    const deleteBtn = wrapper.findAll('button').find((b) => b.text() === 'Delete')
    await deleteBtn?.trigger('click')

    const confirmBtn = wrapper.findAll('button').find((b) => b.text() === 'Delete' && b.element !== deleteBtn?.element)
    await confirmBtn?.trigger('click')

    expect(fetch).toHaveBeenCalled()
  })

  it('cancel button closes delete dialog', async () => {
    const authStore = useAuthStore()
    authStore.accessToken = 'token'

    const wrapper = mount(UserTable, { props: { users: [mockUser] } })

    const deleteBtn = wrapper.findAll('button').find((b) => b.text() === 'Delete')
    await deleteBtn?.trigger('click')
    expect(wrapper.text()).toContain('This cannot be undone')

    const cancelBtn = wrapper.findAll('button').find((b) => b.text() === 'Cancel')
    await cancelBtn?.trigger('click')

    expect(wrapper.text()).not.toContain('This cannot be undone')
  })

  it('shows email and role for each user', () => {
    const second: AdminUser = { ...mockUser, id: 'u2', username: 'bob', email: 'bob@example.com', role: 'ADMIN' }
    const authStore = useAuthStore()
    authStore.accessToken = 'token'

    const wrapper = mount(UserTable, { props: { users: [mockUser, second] } })
    expect(wrapper.text()).toContain('alice@example.com')
    expect(wrapper.text()).toContain('bob@example.com')

    const selects = wrapper.findAll('select')
    expect(selects).toHaveLength(2)
  })
})
