import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import TotpQrCode from '../components/TotpQrCode.vue'

const mockToCanvas = vi.fn()

vi.mock('qrcode', () => ({
  default: { toCanvas: (...args: unknown[]) => mockToCanvas(...args) },
}))

function mountComponent(props: { secret: string; qrUri: string }) {
  return mount(TotpQrCode, {
    props,
  })
}

describe('totpQrCode', () => {
  it('renders canvas element for QR code', () => {
    const wrapper = mountComponent({ secret: 'TESTSECRET', qrUri: 'otpauth://totp/test' })
    expect(wrapper.find('canvas').exists()).toBe(true)
  })

  it('shows manual secret text', () => {
    const wrapper = mountComponent({ secret: 'JBSWY3DPEHPK3PXP', qrUri: 'otpauth://totp/test' })
    expect(wrapper.text()).toContain('JBSWY3DPEHPK3PXP')
  })

  it('updates QR on prop change', async () => {
    const wrapper = mountComponent({ secret: 'SECRET1', qrUri: 'otpauth://totp/first' })
    mockToCanvas.mockClear()

    await wrapper.setProps({ secret: 'SECRET2', qrUri: 'otpauth://totp/second' })
    await wrapper.vm.$nextTick()

    expect(mockToCanvas).toHaveBeenCalled()
  })
})
