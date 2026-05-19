import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import Dropdown from '../components/Dropdown.vue'

describe('dropdown', () => {
  it('starts closed', () => {
    const wrapper = mount(Dropdown, {
      slots: { trigger: '<span>Open</span>', default: '<button>Item</button>' },
    })
    expect(wrapper.find('[data-testid="dropdown-menu"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="dropdown-trigger"]').attributes('aria-expanded')).toBe('false')
  })

  it('opens on trigger click', async () => {
    const wrapper = mount(Dropdown, {
      attachTo: document.body,
      slots: { trigger: '<span>Open</span>', default: '<button data-testid="item">Item</button>' },
    })
    await wrapper.get('[data-testid="dropdown-trigger"]').trigger('click')
    expect(wrapper.find('[data-testid="dropdown-menu"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="dropdown-trigger"]').attributes('aria-expanded')).toBe('true')
    wrapper.unmount()
  })

  it('closes on Escape', async () => {
    const wrapper = mount(Dropdown, {
      attachTo: document.body,
      slots: { trigger: '<span>Open</span>', default: '<button>Item</button>' },
    })
    await wrapper.get('[data-testid="dropdown-trigger"]').trigger('click')
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-testid="dropdown-menu"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('closes on outside click', async () => {
    const wrapper = mount(Dropdown, {
      attachTo: document.body,
      slots: { trigger: '<span>Open</span>', default: '<button>Item</button>' },
    })
    await wrapper.get('[data-testid="dropdown-trigger"]').trigger('click')
    // Click a fresh element outside the dropdown.
    const outside = document.createElement('div')
    document.body.appendChild(outside)
    outside.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-testid="dropdown-menu"]').exists()).toBe(false)
    outside.remove()
    wrapper.unmount()
  })
})
