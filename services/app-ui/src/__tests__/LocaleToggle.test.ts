import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import LocaleToggle from '../components/LocaleToggle.vue'
import { i18n } from '../i18n'

function mountToggle() {
  return mount(LocaleToggle, { global: { plugins: [i18n] } })
}

describe('localeToggle', () => {
  beforeEach(() => {
    localStorage.clear()
    i18n.global.locale.value = 'en'
  })

  it('shows the British flag while English is active', () => {
    const wrapper = mountToggle()
    expect(wrapper.find('[data-testid="nav-locale"]').exists()).toBe(true)
    expect(wrapper.find('svg').attributes('viewBox')).toBe('0 0 60 30')
  })

  it('switches to Dutch and shows the Dutch flag on click', async () => {
    const wrapper = mountToggle()
    await wrapper.find('[data-testid="nav-locale"]').trigger('click')

    expect(i18n.global.locale.value).toBe('nl')
    expect(localStorage.getItem('ps_locale')).toBe('nl')
    expect(wrapper.find('svg').attributes('viewBox')).toBe('0 0 9 6')
  })

  it('toggles back to English on a second click', async () => {
    const wrapper = mountToggle()
    const button = wrapper.find('[data-testid="nav-locale"]')

    await button.trigger('click')
    await button.trigger('click')

    expect(i18n.global.locale.value).toBe('en')
    expect(wrapper.find('svg').attributes('viewBox')).toBe('0 0 60 30')
  })

  it('gives each instance unique clip-path ids within one app', () => {
    // The extras slot renders the toggle twice (desktop + drawer) in a
    // single app, so the per-instance useId() values must not collide.
    const wrapper = mount(
      { components: { LocaleToggle }, template: '<div><LocaleToggle /><LocaleToggle /></div>' },
      { global: { plugins: [i18n] } },
    )
    const ids = wrapper.findAll('clipPath').map((c) => c.attributes('id'))
    expect(ids).toHaveLength(4)
    expect(new Set(ids).size).toBe(4)
  })
})
