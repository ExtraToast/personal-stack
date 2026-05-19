import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import Spinner from '../components/Spinner.vue'

describe('spinner', () => {
  it('renders with default size (md) and the loading label', () => {
    const wrapper = mount(Spinner)
    const el = wrapper.get('[data-testid="spinner"]')
    expect(el.attributes('aria-label')).toBe('Loading')
    expect(el.classes().join(' ')).toContain('h-4')
  })

  it('omits the aria-label when an empty string is passed', () => {
    const wrapper = mount(Spinner, { props: { label: '' } })
    const el = wrapper.get('[data-testid="spinner"]')
    expect(el.attributes('aria-label')).toBeUndefined()
    expect(el.attributes('aria-hidden')).toBe('true')
  })

  it('renders the lg dimensions when size=lg', () => {
    const wrapper = mount(Spinner, { props: { size: 'lg' } })
    expect(wrapper.get('[data-testid="spinner"]').classes().join(' ')).toContain('h-6')
  })
})
