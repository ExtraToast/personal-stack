import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CallbackView from '../features/auth/views/CallbackView.vue'

const mockReplace = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({
    replace: mockReplace,
  }),
}))

function setSearchParams(params: Record<string, string>) {
  const url = new URL('http://localhost/callback')
  for (const [key, value] of Object.entries(params)) {
    url.searchParams.set(key, value)
  }
  Object.defineProperty(window, 'location', {
    value: { ...window.location, search: url.search },
    writable: true,
  })
}

describe('callbackView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockReplace.mockReset()
  })

  it('shows loading text when no error', () => {
    setSearchParams({ code: 'abc', state: 'xyz' })
    const wrapper = mount(CallbackView)
    expect(wrapper.text()).toContain('Completing sign-in')
  })

  it('shows error when error param is present', async () => {
    setSearchParams({ error: 'access_denied' })
    const wrapper = mount(CallbackView)
    await wrapper.vm.$nextTick()
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Authentication failed: access_denied')
  })

  it('shows error when code or state is missing', async () => {
    setSearchParams({})
    const wrapper = mount(CallbackView)
    await wrapper.vm.$nextTick()
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Invalid callback')
  })
})
