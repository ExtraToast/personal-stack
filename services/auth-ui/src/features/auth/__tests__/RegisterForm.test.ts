import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createRouter, createWebHashHistory } from 'vue-router'
import RegisterForm from '../components/RegisterForm.vue'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [{ path: '/', component: { template: '<div/>' } }],
})

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    register: vi.fn().mockResolvedValue(undefined),
    login: vi.fn().mockResolvedValue(undefined),
    isLoading: false,
    error: null,
  }),
}))

describe('registerForm', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders all required fields', () => {
    const wrapper = mount(RegisterForm, {
      global: { plugins: [router] },
    })
    expect(wrapper.find('#username').exists()).toBe(true)
    expect(wrapper.find('#email').exists()).toBe(true)
    expect(wrapper.find('#password').exists()).toBe(true)
    expect(wrapper.find('#confirmPassword').exists()).toBe(true)
  })

  it('shows validation error when passwords do not match', async () => {
    const wrapper = mount(RegisterForm, {
      global: { plugins: [router] },
    })
    await wrapper.find('#username').setValue('alice')
    await wrapper.find('#email').setValue('alice@example.com')
    await wrapper.find('#password').setValue('password123')
    await wrapper.find('#confirmPassword').setValue('different456')
    await wrapper.find('form').trigger('submit')
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('match')
  })
})
