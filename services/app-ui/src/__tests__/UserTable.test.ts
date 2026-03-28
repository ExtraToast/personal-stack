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
})
