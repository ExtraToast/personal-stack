import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
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
        plugins: [createPinia(), i18n],
        // Nav entries now use RouterLink with { path, hash } targets, which
        // would require a full router. Stub both RouterLink and RouterView so
        // the component mounts without one.
        stubs: ['RouterView', 'RouterLink'],
      },
    })
    expect(wrapper.exists()).toBe(true)
  })
})
