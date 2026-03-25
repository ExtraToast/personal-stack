import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createRouter, createWebHashHistory } from 'vue-router'
import LoginForm from '../components/LoginForm.vue'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [{ path: '/', component: { template: '<div/>' } }],
})

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    login: vi.fn().mockResolvedValue(undefined),
    isLoading: false,
    error: null,
  }),
}))

describe('loginForm', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders username and password inputs', () => {
    const wrapper = mount(LoginForm, {
      global: { plugins: [router] },
    })
    expect(wrapper.find('#username').exists()).toBe(true)
    expect(wrapper.find('#password').exists()).toBe(true)
  })

  it('shows validation error when submitting empty form', async () => {
    const wrapper = mount(LoginForm, {
      global: { plugins: [router] },
    })
    await wrapper.find('form').trigger('submit')
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('required')
  })

  it('renders the register link', () => {
    const wrapper = mount(LoginForm, {
      global: { plugins: [router] },
    })
    expect(
      wrapper.find('a[href*="register"]').exists() || wrapper.text().includes('Register'),
    ).toBe(true)
  })
})
