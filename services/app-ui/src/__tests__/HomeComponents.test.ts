import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { describe, expect, it } from 'vitest'
import { createI18n } from 'vue-i18n'
import AboutSection from '../features/home/components/AboutSection.vue'
import ContactSection from '../features/home/components/ContactSection.vue'
import ExperienceTimeline from '../features/home/components/ExperienceTimeline.vue'
import FooterSection from '../features/home/components/FooterSection.vue'
import ProjectShowcase from '../features/home/components/ProjectShowcase.vue'
import SkillsSection from '../features/home/components/SkillsSection.vue'
import HomeView from '../features/home/views/HomeView.vue'
import en from '../i18n/locales/en'

const i18n = createI18n({
  legacy: false,
  locale: 'en',
  messages: { en },
})

function plugins() {
  return [createPinia(), i18n]
}

describe('aboutSection', () => {
  it('renders the section title', () => {
    const wrapper = mount(AboutSection, { global: { plugins: plugins() } })
    expect(wrapper.text()).toContain('About me')
  })

  it('renders education heading', () => {
    const wrapper = mount(AboutSection, { global: { plugins: plugins() } })
    expect(wrapper.text()).toContain('Education')
  })

  it('renders location info', () => {
    const wrapper = mount(AboutSection, { global: { plugins: plugins() } })
    expect(wrapper.text()).toContain('Enschede, Netherlands')
  })
})

describe('contactSection', () => {
  it('renders the section title', () => {
    const wrapper = mount(ContactSection, { global: { plugins: plugins() } })
    expect(wrapper.text()).toContain('Get in touch')
  })

  it('renders the email CTA button', () => {
    const wrapper = mount(ContactSection, { global: { plugins: plugins() } })
    const link = wrapper.find('a')
    expect(link.attributes('href')).toBe('mailto:info@jorisjonkers.dev')
    expect(link.text()).toContain('Send me an email')
  })

  it('renders the subtitle', () => {
    const wrapper = mount(ContactSection, { global: { plugins: plugins() } })
    expect(wrapper.text()).toContain('project idea')
  })
})

describe('experienceTimeline', () => {
  it('renders the section title', () => {
    const wrapper = mount(ExperienceTimeline, { global: { plugins: plugins() } })
    expect(wrapper.text()).toContain('Experience')
  })

  it('renders experience entries', () => {
    const wrapper = mount(ExperienceTimeline, { global: { plugins: plugins() } })
    expect(wrapper.text()).toContain('Nedap N.V.')
    expect(wrapper.text()).toContain('DynaLynx B.V.')
  })

  it('renders technology tags', () => {
    const wrapper = mount(ExperienceTimeline, { global: { plugins: plugins() } })
    expect(wrapper.text()).toContain('Ruby on Rails')
    expect(wrapper.text()).toContain('Docker')
  })
})

describe('footerSection', () => {
  it('renders copyright with current year', () => {
    const wrapper = mount(FooterSection, { global: { plugins: plugins() } })
    const year = new Date().getFullYear().toString()
    expect(wrapper.text()).toContain(year)
    expect(wrapper.text()).toContain('Joris Jonkers')
  })

  it('renders built-with text', () => {
    const wrapper = mount(FooterSection, { global: { plugins: plugins() } })
    expect(wrapper.text()).toContain('Built with Vue.js, Spring Boot & Kotlin')
  })

  it('renders source code link', () => {
    const wrapper = mount(FooterSection, { global: { plugins: plugins() } })
    const link = wrapper.find('a')
    expect(link.attributes('href')).toBe('https://github.com/ExtraToast/personal-stack')
    expect(link.text()).toContain('Source code')
  })
})

describe('projectShowcase', () => {
  it('renders the section title', () => {
    const wrapper = mount(ProjectShowcase, { global: { plugins: plugins() } })
    expect(wrapper.text()).toContain('Projects')
  })

  it('renders project entries', () => {
    const wrapper = mount(ProjectShowcase, { global: { plugins: plugins() } })
    expect(wrapper.text()).toContain('Personal Stack')
    expect(wrapper.text()).toContain('ESA Blueshell Website')
  })

  it('renders the subtitle', () => {
    const wrapper = mount(ProjectShowcase, { global: { plugins: plugins() } })
    expect(wrapper.text()).toContain('Things I have built or am building')
  })
})

describe('skillsSection', () => {
  it('renders the section title', () => {
    const wrapper = mount(SkillsSection, { global: { plugins: plugins() } })
    expect(wrapper.text()).toContain('Skills')
  })

  it('renders programming languages', () => {
    const wrapper = mount(SkillsSection, { global: { plugins: plugins() } })
    expect(wrapper.text()).toContain('Python')
    expect(wrapper.text()).toContain('Java')
  })

  it('renders frameworks', () => {
    const wrapper = mount(SkillsSection, { global: { plugins: plugins() } })
    expect(wrapper.text()).toContain('Ruby on Rails')
    expect(wrapper.text()).toContain('Vue.js')
  })
})

describe('homeView', () => {
  it('renders the page with child components', () => {
    const wrapper = mount(HomeView, {
      global: {
        plugins: plugins(),
        stubs: {
          AppsGrid: true,
        },
      },
    })
    expect(wrapper.text()).toContain('Joris Jonkers')
    expect(wrapper.text()).toContain('About me')
  })

  it('renders contact and footer sections', () => {
    const wrapper = mount(HomeView, {
      global: {
        plugins: plugins(),
        stubs: {
          AppsGrid: true,
        },
      },
    })
    expect(wrapper.text()).toContain('Get in touch')
    expect(wrapper.text()).toContain('Source code')
  })
})
