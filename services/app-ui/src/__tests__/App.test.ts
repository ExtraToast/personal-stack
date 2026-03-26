import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createI18n } from 'vue-i18n'
import App from '../App.vue'
import en from '../i18n/locales/en'

const i18n = createI18n({
  legacy: false,
  locale: 'en',
  messages: { en },
})

describe('app', () => {
  it('renders without crashing', () => {
    const wrapper = mount(App, {
      global: {
        plugins: [i18n],
        stubs: ['RouterView'],
      },
    })
    expect(wrapper.exists()).toBe(true)
  })
})
