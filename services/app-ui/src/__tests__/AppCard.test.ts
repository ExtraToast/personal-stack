import type { ServiceEntry } from '../features/apps/data/serviceRegistry'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AppCard from '../features/apps/components/AppCard.vue'

const testService: ServiceEntry = {
  permission: 'GRAFANA',
  label: 'Grafana',
  url: 'https://grafana.jorisjonkers.dev',
  iconUrl: 'https://grafana.jorisjonkers.dev/public/img/fav32.png',
  description: 'Observability dashboard',
}

describe('appCard', () => {
  it('renders service label and description', () => {
    const wrapper = mount(AppCard, { props: { service: testService } })
    expect(wrapper.text()).toContain('Grafana')
    expect(wrapper.text()).toContain('Observability dashboard')
  })

  it('links to the service URL', () => {
    const wrapper = mount(AppCard, { props: { service: testService } })
    expect(wrapper.attributes('href')).toBe('https://grafana.jorisjonkers.dev')
  })

  it('opens link in a new tab', () => {
    const wrapper = mount(AppCard, { props: { service: testService } })
    expect(wrapper.attributes('target')).toBe('_blank')
    expect(wrapper.attributes('rel')).toContain('noopener')
  })

  it('renders the service favicon URL', () => {
    const wrapper = mount(AppCard, { props: { service: testService } })
    const image = wrapper.get('img')
    expect(image.attributes('src')).toBe('https://grafana.jorisjonkers.dev/public/img/fav32.png')
    expect(image.attributes('alt')).toBe('Grafana icon')
  })

  it('falls back to a monogram when the icon fails to load', async () => {
    const wrapper = mount(AppCard, { props: { service: testService } })
    await wrapper.get('img').trigger('error')

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.text()).toContain('G')
  })
})
