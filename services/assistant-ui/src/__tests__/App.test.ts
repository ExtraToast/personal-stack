import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import App from '../App.vue'

describe('app', () => {
  it('renders without crashing', () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/', name: 'home', component: { template: '<div />' } }],
    })
    const wrapper = mount(App, {
      global: {
        plugins: [router],
        stubs: ['RouterView'],
      },
    })
    expect(wrapper.exists()).toBe(true)
  })
})
