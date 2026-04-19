import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ProfileDropdown from '../features/account/components/ProfileDropdown.vue'
import { useAuthStore } from '../features/auth'

const mockPush = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
}))

describe('profileDropdown', () => {
  beforeEach(() => {
    const pinia = createPinia()
    setActivePinia(pinia)
    vi.clearAllMocks()

    const authStore = useAuthStore(pinia)
    authStore.user = { sub: '1', username: 'testuser', email: 'test@example.com', firstName: 'Test', lastName: 'User' }
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('renders username when authenticated', () => {
    const wrapper = mount(ProfileDropdown)
    expect(wrapper.text()).toContain('testuser')
  })

  it('does not show dropdown initially', () => {
    const wrapper = mount(ProfileDropdown)
    expect(wrapper.findAll('button')).toHaveLength(1)
    expect(wrapper.text()).not.toContain('Account')
    expect(wrapper.text()).not.toContain('Logout')
  })

  it('opens dropdown on click', async () => {
    const wrapper = mount(ProfileDropdown)
    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Account')
    expect(wrapper.text()).toContain('Logout')
  })

  it('closes dropdown on second click', async () => {
    const wrapper = mount(ProfileDropdown)
    const toggleButton = wrapper.find('button')

    await toggleButton.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Account')

    await toggleButton.trigger('click')
    await flushPromises()
    expect(wrapper.text()).not.toContain('Account')
  })

  it('has Account button that navigates to /account', async () => {
    const wrapper = mount(ProfileDropdown)
    await wrapper.find('button').trigger('click')
    await flushPromises()

    const buttons = wrapper.findAll('button')
    const accountButton = buttons.find((b) => b.text() === 'Account')
    expect(accountButton).toBeDefined()

    await accountButton!.trigger('click')
    expect(mockPush).toHaveBeenCalledWith({ name: 'account' })
  })

  it('has Logout button that calls authStore.logout', async () => {
    const authStore = useAuthStore()
    authStore.logout = vi.fn()

    const wrapper = mount(ProfileDropdown)
    await wrapper.find('button').trigger('click')
    await flushPromises()

    const buttons = wrapper.findAll('button')
    const logoutButton = buttons.find((b) => b.text() === 'Logout')
    expect(logoutButton).toBeDefined()

    await logoutButton!.trigger('click')
    expect(authStore.logout).toHaveBeenCalled()
  })
})
