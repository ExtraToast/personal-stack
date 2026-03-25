import { mount } from '@vue/test-utils'
import { describe, it, expect } from 'vitest'
import App from '../App.vue'

describe('App', () => {
  it('renders without crashing', () => {
    const wrapper = mount(App, {
      global: {
        stubs: ['RouterView'],
      },
    })
    expect(wrapper.exists()).toBe(true)
  })
})
