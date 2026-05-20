import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { describe, expect, it } from 'vitest'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import App from '../App.vue'
import en from '../i18n/locales/en'

const i18n = createI18n({
  legacy: false,
  locale: 'en',
  messages: { en },
})

describe('app', () => {
  it('renders without crashing', () => {
    // AppShell from @personal-stack/vue-common calls `useRoute()`
    // and watches `route.path` to auto-close the mobile drawer on
    // navigation. Without a real router that watch would throw
    // `Cannot read properties of undefined (reading 'path')` —
    // wire a memory router so the watch resolves.
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/', name: 'home', component: { template: '<div />' } }],
    })
    const wrapper = mount(App, {
      global: {
        plugins: [createPinia(), i18n, router],
        stubs: ['RouterView'],
      },
    })
    expect(wrapper.exists()).toBe(true)
  })
})
