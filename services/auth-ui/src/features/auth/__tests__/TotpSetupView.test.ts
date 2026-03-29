import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createRouter, createWebHashHistory } from 'vue-router'
import TotpSetupView from '../views/TotpSetupView.vue'

const mockEnrollTotp = vi.fn()
const mockVerifyTotp = vi.fn()

vi.mock('../services/authService', () => ({
  enrollTotp: (...args: unknown[]) => mockEnrollTotp(...args),
  verifyTotp: (...args: unknown[]) => mockVerifyTotp(...args),
}))

vi.mock('qrcode', () => ({
  default: { toCanvas: vi.fn() },
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

function createTestRouter() {
  return createRouter({
    history: createWebHashHistory(),
    routes: [
      { path: '/', component: { template: '<div/>' } },
      { path: '/totp-setup', name: 'totp-setup', component: { template: '<div/>' } },
      { path: '/dashboard', name: 'dashboard', component: { template: '<div/>' } },
    ],
  })
}

async function mountComponent() {
  const router = createTestRouter()
  const pinia = createPinia()
  setActivePinia(pinia)
  await router.push('/totp-setup')
  await router.isReady()

  const wrapper = mount(TotpSetupView, {
    global: { plugins: [pinia, router] },
  })

  return { wrapper, router }
}

describe('totpSetupView', () => {
  beforeEach(() => {
    mockEnrollTotp.mockReset().mockResolvedValue({ secret: 'JBSWY3DPEHPK3PXP', qrUri: 'otpauth://totp/test' })
    mockVerifyTotp.mockReset().mockResolvedValue(undefined)
    sessionStorage.clear()
  })

  it('calls enrollTotp on mount', async () => {
    await mountComponent()
    expect(mockEnrollTotp).toHaveBeenCalled()
  })

  it('displays QR code component after enrollment', async () => {
    const { wrapper } = await mountComponent()
    await vi.waitFor(() => {
      expect(wrapper.find('canvas').exists()).toBe(true)
    })
  })

  it('shows secret key for manual entry', async () => {
    const { wrapper } = await mountComponent()
    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('JBSWY3DPEHPK3PXP')
    })
  })

  it('verify form accepts 6-digit code', async () => {
    const { wrapper } = await mountComponent()
    await vi.waitFor(() => {
      expect(wrapper.find('#totp-code').exists()).toBe(true)
    })
    const input = wrapper.find('#totp-code')
    expect(input.attributes('maxlength')).toBe('6')
  })

  it('successful verification redirects to dashboard', async () => {
    const { wrapper, router } = await mountComponent()
    const pushSpy = vi.spyOn(router, 'push')

    await vi.waitFor(() => {
      expect(wrapper.find('#totp-code').exists()).toBe(true)
    })

    await wrapper.find('#totp-code').setValue('123456')
    await wrapper.find('form').trigger('submit')
    await wrapper.vm.$nextTick()

    await vi.waitFor(() => {
      expect(mockVerifyTotp).toHaveBeenCalledWith('123456')
      expect(pushSpy).toHaveBeenCalledWith({ name: 'dashboard' })
    })
  })

  it('shows error on enrollment failure', async () => {
    mockEnrollTotp.mockRejectedValue(new Error('Enrollment failed'))
    const { wrapper } = await mountComponent()
    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('Failed to load TOTP setup')
    })
  })

  it('shows error on verification failure', async () => {
    const { wrapper } = await mountComponent()

    await vi.waitFor(() => {
      expect(wrapper.find('#totp-code').exists()).toBe(true)
    })

    mockVerifyTotp.mockRejectedValue(new Error('Invalid code'))

    await wrapper.find('#totp-code').setValue('999999')
    await wrapper.find('form').trigger('submit')

    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('Code verification failed')
    })
  })

  it('uses pendingRedirect from sessionStorage after setup', async () => {
    sessionStorage.setItem('pendingRedirect', '/api/oauth2/authorize?client_id=abc')

    // Mock window.location.href
    const hrefSetter = vi.fn()
    const originalLocation = window.location
    Object.defineProperty(window, 'location', {
      value: {
        ...originalLocation,
        get href() {
          return ''
        },
        set href(val: string) {
          hrefSetter(val)
        },
      },
      writable: true,
      configurable: true,
    })

    const { wrapper } = await mountComponent()

    await vi.waitFor(() => {
      expect(wrapper.find('#totp-code').exists()).toBe(true)
    })

    await wrapper.find('#totp-code').setValue('123456')
    await wrapper.find('form').trigger('submit')

    await vi.waitFor(() => {
      expect(hrefSetter).toHaveBeenCalledWith('/api/oauth2/authorize?client_id=abc')
    })

    expect(sessionStorage.getItem('pendingRedirect')).toBeNull()

    // Restore
    Object.defineProperty(window, 'location', {
      value: originalLocation,
      writable: true,
      configurable: true,
    })
  })
})
