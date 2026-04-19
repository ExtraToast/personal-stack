import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AccountView from '../features/account/views/AccountView.vue'
import { useAuthStore } from '../features/auth'

const mockPush = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
}))

vi.mock('../features/account/services/accountService', () => ({
  fetchProfile: vi.fn(),
  updateProfile: vi.fn(),
  changePassword: vi.fn(),
}))

const mockProfile = {
  id: '1',
  username: 'alice',
  email: 'alice@example.com',
  firstName: 'Alice',
  lastName: 'Smith',
  role: 'USER',
  totpEnabled: false,
  createdAt: '2025-01-01T00:00:00Z',
}

describe('accountView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('renders profile data after loading', async () => {
    const { fetchProfile } = await import('../features/account/services/accountService')
    vi.mocked(fetchProfile).mockResolvedValue(mockProfile)

    const authStore = useAuthStore()
    authStore.user = { sub: '1', username: 'alice', email: 'alice@example.com', firstName: 'Test', lastName: 'User' }

    const wrapper = mount(AccountView)
    await flushPromises()

    expect(wrapper.text()).toContain('alice')
    expect(wrapper.text()).toContain('alice@example.com')
  })

  it('redirects to home when not authenticated', async () => {
    const authStore = useAuthStore()
    authStore.user = null

    mount(AccountView)
    await flushPromises()

    expect(mockPush).toHaveBeenCalledWith('/')
  })

  it('saves profile on button click', async () => {
    const { fetchProfile, updateProfile } = await import('../features/account/services/accountService')
    vi.mocked(fetchProfile).mockResolvedValue(mockProfile)
    vi.mocked(updateProfile).mockResolvedValue({ ...mockProfile, firstName: 'Bob' })

    const authStore = useAuthStore()
    authStore.user = { sub: '1', username: 'alice', email: 'alice@example.com', firstName: 'Test', lastName: 'User' }

    const wrapper = mount(AccountView)
    await flushPromises()

    const saveButton = wrapper.findAll('button').find((b) => b.text() === 'Save')
    expect(saveButton).toBeDefined()

    await saveButton!.trigger('click')
    await flushPromises()

    expect(updateProfile).toHaveBeenCalled()
    expect(wrapper.text()).toContain('Profile updated')
  })

  it('shows error when password change fails', async () => {
    const { fetchProfile, changePassword } = await import('../features/account/services/accountService')
    vi.mocked(fetchProfile).mockResolvedValue(mockProfile)
    vi.mocked(changePassword).mockRejectedValue(new Error('Bad request'))

    const authStore = useAuthStore()
    authStore.user = { sub: '1', username: 'alice', email: 'alice@example.com', firstName: 'Test', lastName: 'User' }

    const wrapper = mount(AccountView)
    await flushPromises()

    // Fill in password fields
    await wrapper.find('#currentPassword').setValue('oldpass1')
    await wrapper.find('#newPassword').setValue('newpass123')
    await wrapper.find('#confirmPassword').setValue('newpass123')

    const changeBtn = wrapper.findAll('button').find((b) => b.text() === 'Change password')
    expect(changeBtn).toBeDefined()

    await changeBtn!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Failed to change password')
  })

  it('validates password match before submitting', async () => {
    const { fetchProfile, changePassword } = await import('../features/account/services/accountService')
    vi.mocked(fetchProfile).mockResolvedValue(mockProfile)

    const authStore = useAuthStore()
    authStore.user = { sub: '1', username: 'alice', email: 'alice@example.com', firstName: 'Test', lastName: 'User' }

    const wrapper = mount(AccountView)
    await flushPromises()

    // Fill in mismatched passwords
    await wrapper.find('#currentPassword').setValue('oldpass1')
    await wrapper.find('#newPassword').setValue('newpass123')
    await wrapper.find('#confirmPassword').setValue('differentpass')

    const changeBtn = wrapper.findAll('button').find((b) => b.text() === 'Change password')
    await changeBtn!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Passwords do not match')
    expect(changePassword).not.toHaveBeenCalled()
  })
})
