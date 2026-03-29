import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createRouter, createWebHashHistory } from 'vue-router'

import CheckEmailView from '../views/CheckEmailView.vue'
import ConfirmEmailView from '../views/ConfirmEmailView.vue'
import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'

const mockConfirmEmail = vi.fn()
const mockResendConfirmation = vi.fn()

vi.mock('../services/authService', () => ({
  confirmEmail: (...args: unknown[]) => mockConfirmEmail(...args),
  resendConfirmation: (...args: unknown[]) => mockResendConfirmation(...args),
}))

vi.mock('@personal-stack/vue-common', () => ({
  useAuth: () => ({
    user: null,
    isAuthenticated: false,
    setTokens: vi.fn(),
    getAccessToken: () => 'fake-token',
    getRefreshToken: () => null,
    logout: vi.fn(),
  }),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    login: vi.fn(),
    verifyTotpChallenge: vi.fn(),
    isLoading: false,
    error: null,
    totpRequired: false,
  }),
}))

function createTestRouter() {
  return createRouter({
    history: createWebHashHistory(),
    routes: [
      { path: '/', component: { template: '<div/>' } },
      { path: '/login', name: 'login', component: { template: '<div/>' } },
      { path: '/register', name: 'register', component: { template: '<div/>' } },
      { path: '/check-email', name: 'check-email', component: { template: '<div/>' } },
      { path: '/confirm-email', name: 'confirm-email', component: { template: '<div/>' } },
    ],
  })
}

describe('loginView', () => {
  it('renders the LoginForm component', async () => {
    const router = createTestRouter()
    const pinia = createPinia()
    setActivePinia(pinia)
    await router.push('/login')
    await router.isReady()

    const wrapper = mount(LoginView, {
      global: { plugins: [pinia, router] },
    })

    expect(wrapper.find('.min-h-screen').exists()).toBe(true)
    // LoginForm renders a form element
    expect(wrapper.findComponent({ name: 'LoginForm' }).exists()).toBe(true)
  })

  it('has correct layout classes', async () => {
    const router = createTestRouter()
    const pinia = createPinia()
    setActivePinia(pinia)
    await router.push('/login')
    await router.isReady()

    const wrapper = mount(LoginView, {
      global: { plugins: [pinia, router] },
    })

    const container = wrapper.find('.flex.min-h-screen')
    expect(container.exists()).toBe(true)
  })
})

describe('registerView', () => {
  it('renders the RegisterForm component', async () => {
    const router = createTestRouter()
    const pinia = createPinia()
    setActivePinia(pinia)
    await router.push('/register')
    await router.isReady()

    const wrapper = mount(RegisterView, {
      global: { plugins: [pinia, router] },
    })

    expect(wrapper.find('.min-h-screen').exists()).toBe(true)
    expect(wrapper.findComponent({ name: 'RegisterForm' }).exists()).toBe(true)
  })
})

describe('checkEmailView', () => {
  beforeEach(() => {
    mockResendConfirmation.mockReset().mockResolvedValue({ message: 'sent' })
  })

  async function mountCheckEmail(query: Record<string, string> = {}) {
    const router = createTestRouter()
    const pinia = createPinia()
    setActivePinia(pinia)
    await router.push({ path: '/check-email', query })
    await router.isReady()

    return mount(CheckEmailView, {
      global: { plugins: [pinia, router] },
    })
  }

  it('displays the email from query parameter', async () => {
    const wrapper = await mountCheckEmail({ email: 'alice@example.com' })
    expect(wrapper.text()).toContain('alice@example.com')
  })

  it('displays check inbox heading', async () => {
    const wrapper = await mountCheckEmail({ email: 'test@test.com' })
    expect(wrapper.text()).toContain('Check your inbox')
  })

  it('resend button calls resendConfirmation and shows success', async () => {
    const wrapper = await mountCheckEmail({ email: 'alice@example.com' })

    await wrapper.find('button').trigger('click')

    await vi.waitFor(() => {
      expect(mockResendConfirmation).toHaveBeenCalledWith('alice@example.com')
      expect(wrapper.text()).toContain('Confirmation email sent')
    })
  })

  it('shows error message when resend fails', async () => {
    mockResendConfirmation.mockRejectedValue(new Error('fail'))
    const wrapper = await mountCheckEmail({ email: 'alice@example.com' })

    await wrapper.find('button').trigger('click')

    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('Failed to resend confirmation email')
    })
  })

  it('disables resend button when no email in query', async () => {
    const wrapper = await mountCheckEmail({})
    const button = wrapper.find('button')
    expect(button.attributes('disabled')).toBeDefined()
  })

  it('has a back to login link', async () => {
    const wrapper = await mountCheckEmail({ email: 'a@b.com' })
    expect(wrapper.text()).toContain('Back to login')
  })
})

describe('confirmEmailView', () => {
  beforeEach(() => {
    mockConfirmEmail.mockReset().mockResolvedValue({ message: 'Confirmed' })
    mockResendConfirmation.mockReset().mockResolvedValue({ message: 'sent' })
  })

  async function mountConfirmEmail(query: Record<string, string> = {}) {
    const router = createTestRouter()
    const pinia = createPinia()
    setActivePinia(pinia)
    await router.push({ path: '/confirm-email', query })
    await router.isReady()

    return mount(ConfirmEmailView, {
      global: { plugins: [pinia, router] },
    })
  }

  it('calls confirmEmail on mount with token', async () => {
    const wrapper = await mountConfirmEmail({ token: 'abc-123' })

    await vi.waitFor(() => {
      expect(mockConfirmEmail).toHaveBeenCalledWith('abc-123')
      expect(wrapper.text()).toContain('Email confirmed')
    })
  })

  it('shows success state with sign in link', async () => {
    const wrapper = await mountConfirmEmail({ token: 'valid-token' })

    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('Email confirmed')
      expect(wrapper.text()).toContain('Sign in')
    })
  })

  it('shows error when no token provided', async () => {
    const wrapper = await mountConfirmEmail({})

    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('No confirmation token provided')
    })
  })

  it('shows error when confirmEmail fails', async () => {
    mockConfirmEmail.mockRejectedValue(new Error('expired'))
    const wrapper = await mountConfirmEmail({ token: 'bad-token' })

    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('invalid or has expired')
    })
  })

  it('allows resending confirmation from error state', async () => {
    mockConfirmEmail.mockRejectedValue(new Error('expired'))
    const wrapper = await mountConfirmEmail({ token: 'bad-token' })

    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('Confirmation failed')
    })

    const emailInput = wrapper.find('input[type="email"]')
    await emailInput.setValue('alice@example.com')
    await wrapper.find('button').trigger('click')

    await vi.waitFor(() => {
      expect(mockResendConfirmation).toHaveBeenCalledWith('alice@example.com')
      expect(wrapper.text()).toContain('Confirmation email sent')
    })
  })

  it('shows resend error on failure', async () => {
    mockConfirmEmail.mockRejectedValue(new Error('expired'))
    mockResendConfirmation.mockRejectedValue(new Error('fail'))
    const wrapper = await mountConfirmEmail({ token: 'bad-token' })

    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('Confirmation failed')
    })

    const emailInput = wrapper.find('input[type="email"]')
    await emailInput.setValue('alice@example.com')
    await wrapper.find('button').trigger('click')

    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('Failed to resend confirmation email')
    })
  })
})
