import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createI18n } from 'vue-i18n'
import ProfileDropdown from '../features/account/components/ProfileDropdown.vue'
import { useAuthStore } from '../features/auth'
import en from '../i18n/locales/en'

const mockPush = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
}))

const i18n = createI18n({ legacy: false, locale: 'en', messages: { en } })

function mountDropdown() {
  return mount(ProfileDropdown, { global: { plugins: [i18n] } })
}

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
    const wrapper = mountDropdown()
    expect(wrapper.text()).toContain('testuser')
  })

  it('does not show dropdown initially', () => {
    const wrapper = mountDropdown()
    expect(wrapper.findAll('button')).toHaveLength(1)
    expect(wrapper.text()).not.toContain('Account')
    expect(wrapper.text()).not.toContain('Logout')
  })

  it('opens dropdown on click', async () => {
    const wrapper = mountDropdown()
    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('My Apps')
    expect(wrapper.text()).toContain('Account')
    expect(wrapper.text()).toContain('Logout')
  })

  it('closes dropdown on second click', async () => {
    const wrapper = mountDropdown()
    const toggleButton = wrapper.find('button')

    await toggleButton.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Account')

    await toggleButton.trigger('click')
    await flushPromises()
    expect(wrapper.text()).not.toContain('Account')
  })

  it('navigates to the apps route from the My Apps entry', async () => {
    const wrapper = mountDropdown()
    await wrapper.find('button').trigger('click')
    await flushPromises()

    await wrapper.find('[data-testid="dropdown-my-apps"]').trigger('click')
    expect(mockPush).toHaveBeenCalledWith({ name: 'apps' })
  })

  it('navigates to the account route from the Account entry', async () => {
    const wrapper = mountDropdown()
    await wrapper.find('button').trigger('click')
    await flushPromises()

    await wrapper.find('[data-testid="dropdown-account"]').trigger('click')
    expect(mockPush).toHaveBeenCalledWith({ name: 'account' })
  })

  it('hides the Admin entry for non-admin users', async () => {
    const wrapper = mountDropdown()
    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="dropdown-admin"]').exists()).toBe(false)
  })

  it('shows the Admin entry for admins and navigates to the admin route', async () => {
    const authStore = useAuthStore()
    authStore.roles = ['ROLE_ADMIN']

    const wrapper = mountDropdown()
    await wrapper.find('button').trigger('click')
    await flushPromises()

    const adminEntry = wrapper.find('[data-testid="dropdown-admin"]')
    expect(adminEntry.exists()).toBe(true)

    await adminEntry.trigger('click')
    expect(mockPush).toHaveBeenCalledWith({ name: 'admin' })
  })

  it('calls authStore.logout from the Logout entry', async () => {
    const authStore = useAuthStore()
    authStore.logout = vi.fn()

    const wrapper = mountDropdown()
    await wrapper.find('button').trigger('click')
    await flushPromises()

    await wrapper.find('[data-testid="dropdown-logout"]').trigger('click')
    expect(authStore.logout).toHaveBeenCalled()
  })
})
