import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createI18n } from 'vue-i18n'
import HeroSection from '../features/home/components/HeroSection.vue'
import en from '../i18n/locales/en'

const i18n = createI18n({
  legacy: false,
  locale: 'en',
  messages: { en },
})

function mountHero() {
  return mount(HeroSection, {
    global: { plugins: [i18n] },
  })
}

describe('heroSection', () => {
  it('renders the developer name', () => {
    const wrapper = mountHero()
    expect(wrapper.text()).toContain('Joris Jonkers')
  })

  it('renders the GitHub link', () => {
    const wrapper = mountHero()
    const link = wrapper.findAll('a').find((a) => a.text() === 'GitHub')
    expect(link).toBeDefined()
    expect(link?.attributes('href')).toBe('https://github.com/ExtraToast')
  })

  it('renders the LinkedIn link', () => {
    const wrapper = mountHero()
    const link = wrapper.findAll('a').find((a) => a.text() === 'LinkedIn')
    expect(link).toBeDefined()
  })

  it('renders the email link', () => {
    const wrapper = mountHero()
    const link = wrapper.findAll('a').find((a) => a.text() === 'Email')
    expect(link).toBeDefined()
    expect(link?.attributes('href')).toBe('mailto:info@jorisjonkers.dev')
  })

  it('renders the role subtitle', () => {
    const wrapper = mountHero()
    expect(wrapper.text()).toContain('Jr. Software Engineer')
  })
})
