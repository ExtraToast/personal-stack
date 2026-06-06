import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  buildGitHubAppInstallationUrl,
  buildGitHubAppInstallationUrlForRepo,
  DEFAULT_GITHUB_APP_SLUG,
  GITHUB_APP_REQUESTED_PERMISSIONS,
  GITHUB_APP_SLUG_ENV_KEY,
  githubAppLinks,
  isValidGitHubAppSlug,
  isValidGitHubOwner,
  parseGitHubRepositoryOwner,
  parseGitHubRepositoryUrl,
  resolveGitHubAppSlug,
} from '../services/githubAppLinks'

describe('githubAppLinks', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it.each([
    ['git@github.com:ExtraToast/personal-stack.git', 'ExtraToast', 'personal-stack'],
    ['git@github.com:ExtraToast/personal-stack', 'ExtraToast', 'personal-stack'],
    ['ssh://git@github.com/ExtraToast/personal-stack.git', 'ExtraToast', 'personal-stack'],
    ['https://github.com/ExtraToast/personal-stack.git', 'ExtraToast', 'personal-stack'],
    ['https://github.com/ExtraToast/personal-stack', 'ExtraToast', 'personal-stack'],
    ['https://github.com/ExtraToast/personal-stack/', 'ExtraToast', 'personal-stack'],
    ['https://x-access-token:tok@github.com/ExtraToast/repo.git', 'ExtraToast', 'repo'],
  ])('parses owner and repo from %s', (repoUrl, owner, repo) => {
    expect(parseGitHubRepositoryUrl(repoUrl)).toEqual({ owner, repo })
    expect(parseGitHubRepositoryOwner(repoUrl)).toBe(owner)
  })

  it.each([
    'not-a-url',
    'git@github.com:onlyowner',
    'https://github.com/onlyowner',
    'https://github.com/owner/repo/issues',
    '',
  ])('returns null for invalid repo URL format %s', (repoUrl) => {
    expect(parseGitHubRepositoryUrl(repoUrl)).toBeNull()
    expect(parseGitHubRepositoryOwner(repoUrl)).toBeNull()
  })

  it.each([
    { owner: 'ExtraToast', expected: true },
    { owner: 'octo-org-1', expected: true },
    { owner: 'a', expected: true },
    { owner: '', expected: false },
    { owner: '-octo', expected: false },
    { owner: 'octo-', expected: false },
    { owner: 'octo--org', expected: false },
    { owner: 'octo/org', expected: false },
    { owner: 'octo org', expected: false },
    { owner: 'abcdefghijklmnopqrstuvwxyz0123456789abcd', expected: false },
  ])('validates owner $owner as $expected', ({ owner, expected }) => {
    expect(isValidGitHubOwner(owner)).toBe(expected)
  })

  it('rejects repo URLs with invalid owners', () => {
    expect(parseGitHubRepositoryUrl('git@github.com:-octo/repo.git')).toBeNull()
    expect(parseGitHubRepositoryUrl('https://github.com/octo--org/repo')).toBeNull()
  })

  it.each([
    { slug: 'personal-stack-agents', expected: true },
    { slug: 'custom-app-1', expected: true },
    { slug: 'a', expected: true },
    { slug: '', expected: false },
    { slug: 'Custom-App', expected: false },
    { slug: 'custom_app', expected: false },
    { slug: '-custom-app', expected: false },
    { slug: 'custom-app-', expected: false },
  ])('validates app slug $slug as $expected', ({ slug, expected }) => {
    expect(isValidGitHubAppSlug(slug)).toBe(expected)
  })

  it('falls back to the default slug when no env or override is configured', () => {
    expect(resolveGitHubAppSlug()).toBe(DEFAULT_GITHUB_APP_SLUG)
  })

  it('uses the env slug when configured', () => {
    vi.stubEnv(GITHUB_APP_SLUG_ENV_KEY, 'custom-agents-app')

    expect(resolveGitHubAppSlug()).toBe('custom-agents-app')
  })

  it('prefers an explicit slug override over the env slug', () => {
    vi.stubEnv(GITHUB_APP_SLUG_ENV_KEY, 'env-agents-app')

    expect(resolveGitHubAppSlug('override-agents-app')).toBe('override-agents-app')
  })

  it('rejects an invalid configured slug', () => {
    vi.stubEnv(GITHUB_APP_SLUG_ENV_KEY, 'Bad Slug')

    expect(() => resolveGitHubAppSlug()).toThrow('Invalid GitHub App slug: Bad Slug')
    expect(() => resolveGitHubAppSlug('also_bad')).toThrow('Invalid GitHub App slug: also_bad')
  })

  it('builds the canonical GitHub App installation URL with owner state', () => {
    expect(buildGitHubAppInstallationUrl('ExtraToast')).toBe(
      'https://github.com/apps/personal-stack-agents/installations/new?state=ExtraToast',
    )
    expect(buildGitHubAppInstallationUrlForRepo('git@github.com:ExtraToast/personal-stack.git')).toBe(
      'https://github.com/apps/personal-stack-agents/installations/new?state=ExtraToast',
    )
  })

  it('safely encodes the app slug path segment', () => {
    expect(buildGitHubAppInstallationUrl('ExtraToast', 'custom-app')).toBe(
      'https://github.com/apps/custom-app/installations/new?state=ExtraToast',
    )
    expect(() => buildGitHubAppInstallationUrl('ExtraToast', 'custom/app')).toThrow(
      'Invalid GitHub App slug: custom/app',
    )
  })

  it('rejects invalid owners before building URLs', () => {
    expect(() => buildGitHubAppInstallationUrl('octo/org')).toThrow('Invalid GitHub owner: octo/org')
    expect(buildGitHubAppInstallationUrlForRepo('not-a-url')).toBeNull()
  })

  it('mirrors the backend requested permissions without administration', () => {
    expect(GITHUB_APP_REQUESTED_PERMISSIONS).toEqual([
      { key: 'contents', access: 'write', label: 'Contents' },
      { key: 'pull_requests', access: 'write', label: 'Pull requests' },
      { key: 'actions', access: 'write', label: 'Actions' },
    ])
    expect(GITHUB_APP_REQUESTED_PERMISSIONS.map((permission) => permission.key)).not.toContain('administration')
    expect(Object.isFrozen(GITHUB_APP_REQUESTED_PERMISSIONS)).toBe(true)
    expect(Object.isFrozen(githubAppLinks)).toBe(true)
  })
})
