import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createRouter, createWebHashHistory } from 'vue-router'
import RegisterForm from '../components/RegisterForm.vue'

const mockRegister = vi.fn().mockResolvedValue(undefined)
const mockLogin = vi.fn().mockResolvedValue(undefined)

let mockIsLoading = false
let mockError: string | null = null

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    register: (...args: unknown[]) => mockRegister(...args),
    login: (...args: unknown[]) => mockLogin(...args),
    get isLoading() {
      return mockIsLoading
    },
    get error() {
      return mockError
    },
    set error(val: string | null) {
      mockError = val
    },
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
    ],
  })
}

function mountComponent() {
  const router = createTestRouter()
  const pinia = createPinia()
  setActivePinia(pinia)
  return mount(RegisterForm, {
    global: { plugins: [pinia, router] },
  })
}

describe('registerForm', () => {
  beforeEach(() => {
    mockIsLoading = false
    mockError = null
    mockRegister.mockReset().mockResolvedValue(undefined)
    mockLogin.mockReset().mockResolvedValue(undefined)
  })

  it('renders all required fields', () => {
    const wrapper = mountComponent()
    expect(wrapper.find('#username').exists()).toBe(true)
    expect(wrapper.find('#email').exists()).toBe(true)
    expect(wrapper.find('#password').exists()).toBe(true)
    expect(wrapper.find('#confirmPassword').exists()).toBe(true)
  })

  it('shows loading state during registration', () => {
    mockIsLoading = true
    const wrapper = mountComponent()
    const button = wrapper.find('button[type="submit"]')
    expect(button.text()).toContain('Creating account')
    expect(button.attributes('disabled')).toBeDefined()
  })

  it('shows error on failed registration', () => {
    mockError = 'Username already taken'
    const wrapper = mountComponent()
    expect(wrapper.text()).toContain('Username already taken')
  })

  it('login link is present', () => {
    const wrapper = mountComponent()
    const link = wrapper.find('a[href*="login"]')
    expect(link.exists()).toBe(true)
    expect(link.text()).toContain('Sign in')
  })

  it('validates required fields', async () => {
    const wrapper = mountComponent()
    await wrapper.find('form').trigger('submit')
    await wrapper.vm.$nextTick()
    // Username requires min 3 chars
    expect(wrapper.text()).toMatch(/at least|required/i)
  })

  it('validates email format', async () => {
    const wrapper = mountComponent()
    await wrapper.find('#username').setValue('alice')
    await wrapper.find('#email').setValue('not-an-email')
    await wrapper.find('#password').setValue('password123')
    await wrapper.find('#confirmPassword').setValue('password123')
    await wrapper.find('form').trigger('submit')
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toMatch(/valid email/i)
  })

  it('password confirmation mismatch shows error', async () => {
    const wrapper = mountComponent()
    await wrapper.find('#username').setValue('alice')
    await wrapper.find('#email').setValue('alice@example.com')
    await wrapper.find('#password').setValue('password123')
    await wrapper.find('#confirmPassword').setValue('different456')
    await wrapper.find('form').trigger('submit')
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('match')
  })

  it('successful registration redirects to check-email', async () => {
    const router = createTestRouter()
    const pinia = createPinia()
    setActivePinia(pinia)
    await router.push({ path: '/register' })
    await router.isReady()
    const pushSpy = vi.spyOn(router, 'push')

    const wrapper = mount(RegisterForm, {
      global: { plugins: [pinia, router] },
    })

    await wrapper.find('#username').setValue('alice')
    await wrapper.find('#email').setValue('alice@example.com')
    await wrapper.find('#password').setValue('password123')
    await wrapper.find('#confirmPassword').setValue('password123')
    await wrapper.find('form').trigger('submit')
    await wrapper.vm.$nextTick()

    expect(mockRegister).toHaveBeenCalledWith('alice', 'alice@example.com', 'password123')
    expect(pushSpy).toHaveBeenCalledWith({ name: 'check-email', query: { email: 'alice@example.com' } })
  })
})
