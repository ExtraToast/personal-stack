import type { ServiceEntry } from '../features/apps/data/serviceRegistry'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { defineComponent } from 'vue'
import AppCard from '../features/apps/components/AppCard.vue'

const IconStub = defineComponent({ template: '<span />' })

const testService: ServiceEntry = {
  permission: 'GRAFANA',
  label: 'Grafana',
  url: 'https://grafana.jorisjonkers.dev',
  icon: 'simple-icons:grafana',
  description: 'Observability dashboard',
}

describe('appCard', () => {
  it('renders service label and description', () => {
    const wrapper = mount(AppCard, {
      props: { service: testService },
      global: { stubs: { Icon: IconStub } },
    })
    expect(wrapper.text()).toContain('Grafana')
    expect(wrapper.text()).toContain('Observability dashboard')
  })

  it('links to the service URL', () => {
    const wrapper = mount(AppCard, {
      props: { service: testService },
      global: { stubs: { Icon: IconStub } },
    })
    expect(wrapper.attributes('href')).toBe('https://grafana.jorisjonkers.dev')
  })

  it('opens link in a new tab', () => {
    const wrapper = mount(AppCard, {
      props: { service: testService },
      global: { stubs: { Icon: IconStub } },
    })
    expect(wrapper.attributes('target')).toBe('_blank')
    expect(wrapper.attributes('rel')).toContain('noopener')
  })
})
