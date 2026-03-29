import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createRouter, createWebHashHistory } from 'vue-router'
import LoginForm from '../components/LoginForm.vue'

const mockLogin = vi.fn().mockResolvedValue(undefined)
const mockVerifyTotpChallenge = vi.fn().mockResolvedValue(undefined)

let mockIsLoading = false
let mockError: string | null = null
let mockTotpRequired = false

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    login: mockLogin,
    verifyTotpChallenge: mockVerifyTotpChallenge,
    get isLoading() {
      return mockIsLoading
    },
    get error() {
      return mockError
    },
    set error(val: string | null) {
      mockError = val
    },
    get totpRequired() {
      return mockTotpRequired
    },
    set totpRequired(val: boolean) {
      mockTotpRequired = val
    },
  }),
}))

const mockResendConfirmation = vi.fn()
const mockSessionLogin = vi.fn()

vi.mock('../services/authService', () => ({
  resendConfirmation: (...args: unknown[]) => mockResendConfirmation(...args),
  sessionLogin: (...args: unknown[]) => mockSessionLogin(...args),
}))

function createTestRouter() {
  const router = createRouter({
    history: createWebHashHistory(),
    routes: [
      { path: '/', name: 'home', component: { template: '<div/>' } },
      { path: '/login', name: 'login', component: { template: '<div/>' } },
      { path: '/register', name: 'register', component: { template: '<div/>' } },
      { path: '/totp-setup', name: 'totp-setup', component: { template: '<div/>' } },
    ],
  })
  return router
}

function mountComponent(query: Record<string, string> = {}) {
  const router = createTestRouter()
  const pinia = createPinia()
  setActivePinia(pinia)

  // Push route with query before mounting
  router.push({ path: '/login', query })

  return mount(LoginForm, {
    global: { plugins: [pinia, router] },
  })
}

describe('loginForm', () => {
  beforeEach(() => {
    mockIsLoading = false
    mockError = null
    mockTotpRequired = false
    mockLogin.mockReset().mockResolvedValue(undefined)
    mockVerifyTotpChallenge.mockReset().mockResolvedValue(undefined)
    mockResendConfirmation.mockReset().mockResolvedValue({ message: 'sent' })
    mockSessionLogin.mockReset().mockResolvedValue({ success: true, totpRequired: false })
  })

  it('renders username and password inputs', () => {
    const wrapper = mountComponent()
    expect(wrapper.find('#username').exists()).toBe(true)
    expect(wrapper.find('#password').exists()).toBe(true)
  })

  it('shows validation error when submitting empty form', async () => {
    const wrapper = mountComponent()
    await wrapper.find('form').trigger('submit')
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('required')
  })

  it('renders the register link', () => {
    const wrapper = mountComponent()
    expect(wrapper.find('a[href*="register"]').exists() || wrapper.text().includes('Register')).toBe(true)
  })

  it('shows TOTP input when totpRequired is true', async () => {
    mockTotpRequired = true
    const wrapper = mountComponent()
    await wrapper.vm.$nextTick()
    expect(wrapper.find('#totp-code').exists()).toBe(true)
    expect(wrapper.find('#username').exists()).toBe(false)
  })

  it('tOTP input validates 6-digit code', async () => {
    mockTotpRequired = true
    const wrapper = mountComponent()
    await wrapper.vm.$nextTick()
    const input = wrapper.find('#totp-code')
    expect(input.attributes('maxlength')).toBe('6')
    expect(input.attributes('pattern')).toBe('\\d{6}')
  })

  it('detects OAuth2 redirect context from query param', async () => {
    const router = createTestRouter()
    const pinia = createPinia()
    setActivePinia(pinia)
    await router.push({ path: '/login', query: { redirect: '/api/oauth2/authorize?client_id=abc' } })
    await router.isReady()

    const wrapper = mount(LoginForm, {
      global: { plugins: [pinia, router] },
    })

    await wrapper.find('#username').setValue('user1')
    await wrapper.find('#password').setValue('pass123')
    await wrapper.find('form').trigger('submit')
    await wrapper.vm.$nextTick()

    expect(mockSessionLogin).toHaveBeenCalledWith('user1', 'pass123', undefined)
    expect(mockLogin).not.toHaveBeenCalled()
  })

  it('in OAuth2 context calls sessionLogin instead of regular login', async () => {
    const router = createTestRouter()
    const pinia = createPinia()
    setActivePinia(pinia)
    await router.push({ path: '/login', query: { redirect: '/api/oauth2/authorize?client_id=x' } })
    await router.isReady()

    const wrapper = mount(LoginForm, {
      global: { plugins: [pinia, router] },
    })

    await wrapper.find('#username').setValue('alice')
    await wrapper.find('#password').setValue('secret')
    await wrapper.find('form').trigger('submit')
    await wrapper.vm.$nextTick()

    expect(mockSessionLogin).toHaveBeenCalled()
    expect(mockLogin).not.toHaveBeenCalled()
  })

  it('shows loading state during login', () => {
    mockIsLoading = true
    const wrapper = mountComponent()
    const button = wrapper.find('button[type="submit"]')
    expect(button.text()).toContain('Verifying')
    expect(button.attributes('disabled')).toBeDefined()
  })

  it('shows error message on login failure', async () => {
    mockError = 'Invalid credentials'
    const wrapper = mountComponent()
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Invalid credentials')
  })

  it('submit button is disabled during loading', () => {
    mockIsLoading = true
    const wrapper = mountComponent()
    const button = wrapper.find('button[type="submit"]')
    expect(button.attributes('disabled')).toBeDefined()
  })

  it('shows resend confirmation section when email not confirmed', async () => {
    mockError = 'Your email has not been confirmed'
    const wrapper = mountComponent()
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('new confirmation link')
  })

  it('resend confirmation success shows message', async () => {
    mockError = 'Your email has not been confirmed'
    mockResendConfirmation.mockResolvedValue({ message: 'sent' })
    const wrapper = mountComponent()
    await wrapper.vm.$nextTick()

    const emailInput = wrapper.find('input[type="email"]')
    await emailInput.setValue('test@example.com')
    await wrapper.find('button[type="button"]').trigger('click')
    await wrapper.vm.$nextTick()
    // Wait for the async handler
    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('Confirmation email sent')
    })
  })

  it('resend confirmation error shows message', async () => {
    mockError = 'Your email has not been confirmed'
    mockResendConfirmation.mockRejectedValue(new Error('fail'))
    const wrapper = mountComponent()
    await wrapper.vm.$nextTick()

    const emailInput = wrapper.find('input[type="email"]')
    await emailInput.setValue('test@example.com')
    await wrapper.find('button[type="button"]').trigger('click')
    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('Failed to resend')
    })
  })

  it('form submission calls authStore.login', async () => {
    const wrapper = mountComponent()
    await wrapper.find('#username').setValue('alice')
    await wrapper.find('#password').setValue('password123')
    await wrapper.find('form').trigger('submit')
    await wrapper.vm.$nextTick()
    expect(mockLogin).toHaveBeenCalledWith('alice', 'password123')
  })

  it('tOTP form submission calls authStore.verifyTotpChallenge', async () => {
    mockTotpRequired = true
    const wrapper = mountComponent()
    await wrapper.vm.$nextTick()

    await wrapper.find('#totp-code').setValue('123456')
    await wrapper.find('form').trigger('submit')
    await wrapper.vm.$nextTick()
    expect(mockVerifyTotpChallenge).toHaveBeenCalledWith('123456')
  })

  it('after non-TOTP login redirects to totp-setup', async () => {
    const router = createTestRouter()
    const pinia = createPinia()
    setActivePinia(pinia)
    await router.push({ path: '/login' })
    await router.isReady()
    const pushSpy = vi.spyOn(router, 'push')

    const wrapper = mount(LoginForm, {
      global: { plugins: [pinia, router] },
    })

    await wrapper.find('#username').setValue('alice')
    await wrapper.find('#password').setValue('password123')
    await wrapper.find('form').trigger('submit')
    await wrapper.vm.$nextTick()

    expect(pushSpy).toHaveBeenCalledWith({ name: 'totp-setup' })
  })

  it('error messages display from store', () => {
    mockError = 'Something went wrong'
    const wrapper = mountComponent()
    expect(wrapper.text()).toContain('Something went wrong')
  })

  it('register link navigates to /register', () => {
    const wrapper = mountComponent()
    const link = wrapper.find('a[href*="register"]')
    expect(link.exists()).toBe(true)
    expect(link.attributes('href')).toContain('/register')
  })
})
