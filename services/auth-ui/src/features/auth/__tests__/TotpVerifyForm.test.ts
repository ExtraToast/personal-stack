import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import TotpVerifyForm from '../components/TotpVerifyForm.vue'

function mountComponent() {
  return mount(TotpVerifyForm)
}

function getTotpInputElement(wrapper: ReturnType<typeof mount>) {
  const element = wrapper.find('#totp-code').element
  if (!(element instanceof HTMLInputElement)) {
    throw new TypeError('Expected #totp-code to resolve to an HTMLInputElement')
  }
  return element
}

describe('totpVerifyForm', () => {
  it('focuses the code input on mount', async () => {
    const wrapper = mount(TotpVerifyForm, { attachTo: document.body })

    await wrapper.vm.$nextTick()

    expect(getTotpInputElement(wrapper)).toBe(document.activeElement)
    wrapper.unmount()
  })

  it('input accepts numeric characters', () => {
    const wrapper = mountComponent()
    const input = wrapper.find('#totp-code')
    expect(input.exists()).toBe(true)
    expect(input.attributes('inputmode')).toBe('numeric')
  })

  it('validates code is 6 digits', async () => {
    const wrapper = mountComponent()
    await wrapper.find('#totp-code').setValue('123')
    await wrapper.find('form').trigger('submit')
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('6 digits')
  })

  it('emits submit event with code', async () => {
    const wrapper = mountComponent()
    await wrapper.find('#totp-code').setValue('123456')
    await wrapper.find('form').trigger('submit')
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('submit')).toBeTruthy()
    expect(wrapper.emitted('submit')![0]).toEqual(['123456'])
  })

  it('shows validation error for invalid code', async () => {
    const wrapper = mountComponent()
    await wrapper.find('#totp-code').setValue('abcdef')
    await wrapper.find('form').trigger('submit')
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('6 digits')
  })
})
