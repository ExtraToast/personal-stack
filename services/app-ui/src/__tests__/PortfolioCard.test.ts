import type { Project } from '../features/home/types'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PortfolioCard from '../features/home/components/PortfolioCard.vue'

const baseProject: Project = {
  title: 'Test Project',
  description: 'A test project description.',
  technologies: ['Kotlin', 'Vue 3', 'Docker'],
  githubUrl: 'https://github.com/jorisjonkers/test-project',
  liveUrl: 'https://test-project.jorisjonkers.dev',
}

describe('portfolioCard', () => {
  it('renders the project title', () => {
    const wrapper = mount(PortfolioCard, { props: { project: baseProject } })
    expect(wrapper.text()).toContain('Test Project')
  })

  it('renders the project description', () => {
    const wrapper = mount(PortfolioCard, { props: { project: baseProject } })
    expect(wrapper.text()).toContain('A test project description.')
  })

  it('renders all technology tags', () => {
    const wrapper = mount(PortfolioCard, { props: { project: baseProject } })
    for (const tag of baseProject.technologies) {
      expect(wrapper.text()).toContain(tag)
    }
  })

  it('renders the GitHub link when provided', () => {
    const wrapper = mount(PortfolioCard, { props: { project: baseProject } })
    const githubLink = wrapper.findAll('a').find((a) => a.text() === '> github')
    expect(githubLink).toBeDefined()
    expect(githubLink?.attributes('href')).toBe(baseProject.githubUrl)
  })

  it('renders the Live link when provided', () => {
    const wrapper = mount(PortfolioCard, { props: { project: baseProject } })
    const liveLink = wrapper.findAll('a').find((a) => a.text() === '> live')
    expect(liveLink).toBeDefined()
    expect(liveLink?.attributes('href')).toBe(baseProject.liveUrl)
  })

  it('hides GitHub link when githubUrl is not provided', () => {
    const { githubUrl: _, ...rest } = baseProject
    const project: Project = rest
    const wrapper = mount(PortfolioCard, { props: { project } })
    const githubLink = wrapper.findAll('a').find((a) => a.text() === '> github')
    expect(githubLink).toBeUndefined()
  })

  it('hides Live link when liveUrl is not provided', () => {
    const { liveUrl: _, ...rest } = baseProject
    const project: Project = rest
    const wrapper = mount(PortfolioCard, { props: { project } })
    const liveLink = wrapper.findAll('a').find((a) => a.text() === '> live')
    expect(liveLink).toBeUndefined()
  })
})
