import { mount } from '@vue/test-utils'
import { describe, it, expect } from 'vitest'
import BaseButton from '../components/BaseButton.vue'

describe('BaseButton', () => {
  it('renders label', () => {
    const wrapper = mount(BaseButton, {
      props: { label: 'Click me' },
    })
    expect(wrapper.text()).toContain('Click me')
  })

  it('emits click event', async () => {
    const wrapper = mount(BaseButton, {
      props: { label: 'Click' },
    })
    await wrapper.trigger('click')
    expect(wrapper.emitted('click')).toBeTruthy()
  })

  it('does not emit when disabled', async () => {
    const wrapper = mount(BaseButton, {
      props: { label: 'Click', disabled: true },
    })
    await wrapper.trigger('click')
    expect(wrapper.emitted('click')).toBeUndefined()
  })
})
