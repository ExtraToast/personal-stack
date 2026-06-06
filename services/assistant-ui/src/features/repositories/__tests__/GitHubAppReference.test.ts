import type { DOMWrapper } from '@vue/test-utils'
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import GitHubAppReference from '../components/GitHubAppReference.vue'
import { GITHUB_APP_SLUG_ENV_KEY } from '../services/githubAppLinks'

function expectSafeNewTab(link: Omit<DOMWrapper<Element>, 'exists'>, href: string): void {
  expect(link.attributes('href')).toBe(href)
  expect(link.attributes('target')).toBe('_blank')
  expect(link.attributes('rel')).toBe('noopener noreferrer')
}

describe('gitHubAppReference', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('renders canonical GitHub App links with safe new-tab attributes', () => {
    vi.stubEnv(GITHUB_APP_SLUG_ENV_KEY, 'custom-agents-app')
    const wrapper = mount(GitHubAppReference)

    expectSafeNewTab(wrapper.get('[data-testid="github-app-public-link"]'), 'https://github.com/apps/custom-agents-app')
    expectSafeNewTab(
      wrapper.get('[data-testid="github-app-install-link"]'),
      'https://github.com/apps/custom-agents-app/installations/new',
    )
    expectSafeNewTab(
      wrapper.get('[data-testid="github-app-owner-permissions-link"]'),
      'https://github.com/settings/apps/custom-agents-app/permissions',
    )
    expectSafeNewTab(
      wrapper.get('[data-testid="github-app-owner-installations-link"]'),
      'https://github.com/settings/apps/custom-agents-app/installations',
    )
  })

  it('summarizes requested permissions and installation approval', () => {
    const wrapper = mount(GitHubAppReference)

    expect(wrapper.get('[data-testid="github-app-permission-contents"]').text()).toBe('Contents: write')
    expect(wrapper.get('[data-testid="github-app-permission-pull_requests"]').text()).toBe('Pull requests: write')
    expect(wrapper.get('[data-testid="github-app-permission-actions"]').text()).toBe('Actions: write')
    expect(wrapper.get('[data-testid="github-app-permission-approval-note"]').text()).toContain(
      'Permission changes must be approved on each existing installation',
    )
  })
})
