import type { AppShellNavItem } from '../components/appShellTypes'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import AppShell from '../components/AppShell.vue'

const navItems: AppShellNavItem[] = [
  { label: 'Alpha', to: '/alpha', testid: 'nav-alpha' },
  { label: 'Beta', to: '/beta', testid: 'nav-beta' },
]

function makeRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/alpha', component: { template: '<div />' } },
      { path: '/beta', component: { template: '<div />' } },
      { path: '/account', component: { template: '<div />' } },
    ],
  })
}

describe('appShell', () => {
  beforeEach(() => {
    // useTheme persists to localStorage; reset between tests so
    // the cycler starts from a known position.
    localStorage.clear()
  })

  it('renders the desktop nav links with testids', () => {
    const wrapper = mount(AppShell, {
      props: { navItems, brandMain: 'demo' },
      global: { plugins: [makeRouter()] },
    })
    expect(wrapper.find('[data-testid="nav-alpha"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="nav-beta"]').exists()).toBe(true)
  })

  it('renders the brand with the supplied middle segment', () => {
    const wrapper = mount(AppShell, {
      props: { navItems, brandMain: 'assistant', brandSuffix: '.dev' },
      global: { plugins: [makeRouter()] },
    })
    const link = wrapper.find('[data-testid="app-home-link"]')
    expect(link.exists()).toBe(true)
    expect(link.text()).toContain('assistant')
    expect(link.text()).toContain('.dev')
  })

  it('fold-out menu opens on trigger and closes on backdrop click', async () => {
    const wrapper = mount(AppShell, {
      props: { navItems, brandMain: 'demo' },
      global: { plugins: [makeRouter()] },
      attachTo: document.body,
    })
    expect(wrapper.find('[data-testid="nav-menu"]').exists()).toBe(false)
    await wrapper.find('[data-testid="nav-menu-trigger"]').trigger('click')
    expect(wrapper.find('[data-testid="nav-menu"]').exists()).toBe(true)

    // Click outside the menu root — the document-level handler
    // should detect it and close.
    const outside = document.createElement('button')
    document.body.appendChild(outside)
    outside.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-testid="nav-menu"]').exists()).toBe(false)
    outside.remove()
    wrapper.unmount()
  })

  it('fold-out menu closes on Escape', async () => {
    const wrapper = mount(AppShell, {
      props: { navItems, brandMain: 'demo' },
      global: { plugins: [makeRouter()] },
      attachTo: document.body,
    })
    await wrapper.find('[data-testid="nav-menu-trigger"]').trigger('click')
    expect(wrapper.find('[data-testid="nav-menu"]').exists()).toBe(true)
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-testid="nav-menu"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('renders the slot content under the nav', () => {
    const wrapper = mount(AppShell, {
      props: { navItems, brandMain: 'demo' },
      slots: { default: '<p data-testid="slot-content">payload</p>' },
      global: { plugins: [makeRouter()] },
    })
    expect(wrapper.find('[data-testid="slot-content"]').exists()).toBe(true)
  })

  it('omits the theme toggle when showThemeToggle is false', () => {
    const wrapper = mount(AppShell, {
      props: { navItems, brandMain: 'demo', showThemeToggle: false },
      global: { plugins: [makeRouter()] },
    })
    expect(wrapper.find('[data-testid="nav-theme-toggle"]').exists()).toBe(false)
  })

  it('marks the active nav item via class', async () => {
    const router = makeRouter()
    await router.push('/alpha')
    const wrapper = mount(AppShell, {
      props: { navItems, brandMain: 'demo' },
      global: { plugins: [router] },
    })
    const alpha = wrapper.find('[data-testid="nav-alpha"]')
    // Active style applies bg-[var(--color-surface-elevated)].
    expect(alpha.classes().join(' ')).toContain('bg-[var(--color-surface-elevated)]')
  })
})
