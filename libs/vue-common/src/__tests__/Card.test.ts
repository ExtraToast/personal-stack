import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import Card from '../components/Card.vue'

const router = createRouter({
  history: createMemoryHistory(),
  routes: [{ path: '/x/:id', component: { template: '<div />' } }],
})

describe('card', () => {
  it('renders as <article> without `to`', () => {
    const wrapper = mount(Card, { slots: { default: 'Body' } })
    expect(wrapper.element.tagName.toLowerCase()).toBe('article')
    expect(wrapper.text()).toContain('Body')
  })

  it('renders as <RouterLink> with `to`', () => {
    const wrapper = mount(Card, {
      props: { to: '/x/42' },
      global: { plugins: [router] },
      slots: { default: 'Body' },
    })
    expect(wrapper.element.tagName.toLowerCase()).toBe('a')
    expect(wrapper.attributes('href')).toBe('/x/42')
  })

  it('shows hover affordance class when interactive', () => {
    const wrapper = mount(Card, {
      props: { to: '/x/42' },
      global: { plugins: [router] },
    })
    expect(wrapper.classes().join(' ')).toContain('cursor-pointer')
  })

  it('omits the hover affordance class when interactive=false even with `to`', () => {
    const wrapper = mount(Card, {
      props: { to: '/x/42', interactive: false },
      global: { plugins: [router] },
    })
    expect(wrapper.classes().join(' ')).not.toContain('cursor-pointer')
  })

  it('renders header + footer slots', () => {
    const wrapper = mount(Card, {
      slots: {
        header: '<h3>Header</h3>',
        default: 'Body',
        footer: '<button>Action</button>',
      },
    })
    expect(wrapper.text()).toContain('Header')
    expect(wrapper.text()).toContain('Body')
    expect(wrapper.text()).toContain('Action')
  })
})
