import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import HeroSection from '../features/home/components/HeroSection.vue'

describe('heroSection', () => {
  it('renders the developer name', () => {
    const wrapper = mount(HeroSection)
    expect(wrapper.text()).toContain('Joris Jonkers')
  })

  it('renders the GitHub link', () => {
    const wrapper = mount(HeroSection)
    const githubLink = wrapper.findAll('a').find((a) => a.text() === 'GitHub')
    expect(githubLink).toBeDefined()
    expect(githubLink?.attributes('href')).toBe('https://github.com/jorisjonkers')
  })

  it('renders the LinkedIn link', () => {
    const wrapper = mount(HeroSection)
    const linkedinLink = wrapper.findAll('a').find((a) => a.text() === 'LinkedIn')
    expect(linkedinLink).toBeDefined()
    expect(linkedinLink?.attributes('href')).toBe('https://linkedin.com/in/jorisjonkers')
  })

  it('renders the email link', () => {
    const wrapper = mount(HeroSection)
    const emailLink = wrapper.findAll('a').find((a) => a.text() === 'Email')
    expect(emailLink).toBeDefined()
    expect(emailLink?.attributes('href')).toBe('mailto:joris@jorisjonkers.dev')
  })

  it('renders the role subtitle', () => {
    const wrapper = mount(HeroSection)
    expect(wrapper.text()).toContain('Full-Stack Developer')
  })
})
