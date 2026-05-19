import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import SubmitButton from '../components/SubmitButton.vue'

describe('submitButton', () => {
  it('renders the label and is enabled by default', () => {
    const wrapper = mount(SubmitButton, { props: { label: 'Save' } })
    const button = wrapper.get('button')
    expect(button.text()).toContain('Save')
    expect(button.attributes('disabled')).toBeUndefined()
    expect(button.attributes('aria-busy')).toBe('false')
    expect(button.attributes('data-status')).toBe('idle')
  })

  it('shows the spinner and is non-clickable while pending', async () => {
    const wrapper = mount(SubmitButton, {
      props: { label: 'Save', status: 'pending' },
    })
    expect(wrapper.find('[data-testid="spinner"]').exists()).toBe(true)
    expect(wrapper.get('button').attributes('disabled')).toBeDefined()
    expect(wrapper.get('button').attributes('aria-busy')).toBe('true')
    await wrapper.trigger('click')
    expect(wrapper.emitted('click')).toBeUndefined()
  })

  it('shows the success checkmark in success status', () => {
    const wrapper = mount(SubmitButton, {
      props: { label: 'Save', status: 'success' },
    })
    expect(wrapper.find('svg polyline').exists()).toBe(true)
    expect(wrapper.get('button').attributes('data-status')).toBe('success')
  })

  it('shows the failure × in failure status', () => {
    const wrapper = mount(SubmitButton, {
      props: { label: 'Save', status: 'failure' },
    })
    // Failure renders two lines forming an ×.
    expect(wrapper.findAll('svg line').length).toBe(2)
    expect(wrapper.get('button').attributes('data-status')).toBe('failure')
  })

  it('emits click in idle status', async () => {
    const wrapper = mount(SubmitButton, { props: { label: 'Save' } })
    await wrapper.trigger('click')
    expect(wrapper.emitted('click')).toBeTruthy()
  })

  it('disabled prop blocks clicks even outside pending', async () => {
    const wrapper = mount(SubmitButton, {
      props: { label: 'Save', disabled: true },
    })
    await wrapper.trigger('click')
    expect(wrapper.emitted('click')).toBeUndefined()
  })

  it('variant prop affects classes', () => {
    const primary = mount(SubmitButton, { props: { label: 'p' } })
    const danger = mount(SubmitButton, { props: { label: 'd', variant: 'danger' } })
    expect(primary.get('button').classes().join(' ')).toContain('var(--color-accent)')
    expect(danger.get('button').classes().join(' ')).toContain('bg-red-600')
  })
})
