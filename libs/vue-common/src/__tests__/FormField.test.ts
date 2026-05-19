import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import FormField from '../components/FormField.vue'

describe('formField', () => {
  it('renders the label and forwards the generated id to the slot', () => {
    const wrapper = mount(FormField, {
      props: { label: 'Name' },
      slots: {
        default: '<template #default="{ id }"><input :id="id" data-testid="input" /></template>',
      },
    })
    const label = wrapper.get('label')
    const input = wrapper.get('input')
    expect(label.text()).toContain('Name')
    expect(input.attributes('id')).toBeTruthy()
    expect(label.attributes('for')).toBe(input.attributes('id'))
  })

  it('renders an error message and wires aria attributes when error is set', () => {
    const wrapper = mount(FormField, {
      props: { label: 'Email', error: 'required' },
      slots: {
        default: `<template #default="{ id, describedBy, invalid }">
          <input
            :id="id"
            :aria-describedby="describedBy"
            :aria-invalid="invalid"
            data-testid="input"
          />
        </template>`,
      },
    })
    const errEl = wrapper.get('[data-testid="form-field-error"]')
    expect(errEl.text()).toContain('required')
    const input = wrapper.get('input')
    expect(input.attributes('aria-invalid')).toBe('true')
    expect(input.attributes('aria-describedby')).toBe(errEl.attributes('id'))
  })

  it('renders the hint when no error is present', () => {
    const wrapper = mount(FormField, {
      props: { label: 'Slug', hint: 'lowercase only' },
      slots: { default: '<template #default="{ id }"><input :id="id" /></template>' },
    })
    expect(wrapper.text()).toContain('lowercase only')
    expect(wrapper.find('[data-testid="form-field-error"]').exists()).toBe(false)
  })

  it('renders a required marker when required=true', () => {
    const wrapper = mount(FormField, {
      props: { label: 'Name', required: true },
      slots: { default: '<template #default="{ id }"><input :id="id" /></template>' },
    })
    expect(wrapper.text()).toContain('*')
  })
})
