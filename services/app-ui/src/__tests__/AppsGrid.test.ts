import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { defineComponent } from 'vue'
import AppsGrid from '../features/apps/components/AppsGrid.vue'
import { useAuthStore } from '../features/auth'

const IconStub = defineComponent({ template: '<span />' })

function mountGrid() {
  return mount(AppsGrid, {
    global: {
      stubs: { Icon: IconStub },
    },
  })
}

describe('appsGrid', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('renders nothing when user has no permissions', () => {
    const wrapper = mountGrid()
    expect(wrapper.find('section').exists()).toBe(false)
  })

  it('renders service cards matching user permissions', () => {
    const authStore = useAuthStore()
    authStore.roles = ['ROLE_USER', 'SERVICE_GRAFANA', 'SERVICE_VAULT']

    const wrapper = mountGrid()
    expect(wrapper.find('section').exists()).toBe(true)
    const cards = wrapper.findAll('a')
    expect(cards).toHaveLength(2)
  })

  it('renders all services for admin with all permissions', () => {
    const authStore = useAuthStore()
    authStore.roles = [
      'ROLE_ADMIN',
      'SERVICE_VAULT',
      'SERVICE_MAIL',
      'SERVICE_N8N',
      'SERVICE_GRAFANA',
      'SERVICE_ASSISTANT',
      'SERVICE_TRAEFIK_DASHBOARD',
      'SERVICE_STATUS',
    ]

    const wrapper = mountGrid()
    const cards = wrapper.findAll('a')
    expect(cards).toHaveLength(7)
  })

  it('renders nothing when user has no service permissions', () => {
    const authStore = useAuthStore()
    authStore.roles = ['ROLE_USER']

    const wrapper = mountGrid()
    expect(wrapper.find('section').exists()).toBe(false)
    expect(wrapper.findAll('a')).toHaveLength(0)
  })

  it('filters services correctly by single permission', () => {
    const authStore = useAuthStore()
    authStore.roles = ['ROLE_USER', 'SERVICE_GRAFANA']

    const wrapper = mountGrid()
    const cards = wrapper.findAll('a')
    expect(cards).toHaveLength(1)
    expect(wrapper.text()).toContain('Grafana')
  })

  it('service cards have correct href attributes', () => {
    const authStore = useAuthStore()
    authStore.roles = ['ROLE_USER', 'SERVICE_GRAFANA', 'SERVICE_VAULT']

    const wrapper = mountGrid()
    const links = wrapper.findAll('a')
    const hrefs = links.map((l) => l.attributes('href'))
    expect(hrefs).toContain('https://grafana.jorisjonkers.dev')
    expect(hrefs).toContain('https://vault.jorisjonkers.dev')
  })

  it('service cards have target=_blank', () => {
    const authStore = useAuthStore()
    authStore.roles = ['ROLE_USER', 'SERVICE_GRAFANA']

    const wrapper = mountGrid()
    const links = wrapper.findAll('a')
    for (const link of links) {
      expect(link.attributes('target')).toBe('_blank')
    }
  })
})
