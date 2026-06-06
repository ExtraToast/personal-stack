export interface GitHubRepositorySlug {
  readonly owner: string
  readonly repo: string
}

export interface GitHubAppRequestedPermission {
  readonly key: string
  readonly access: string
  readonly label: string
}

export interface GitHubAppLinksApi {
  readonly requestedPermissions: ReadonlyArray<GitHubAppRequestedPermission>
  readonly isValidGitHubAppSlug: (slug: string) => boolean
  readonly resolveGitHubAppSlug: (override?: string | null) => string
  readonly isValidGitHubOwner: (owner: string) => boolean
  readonly parseGitHubRepositoryUrl: (repoUrl: string) => GitHubRepositorySlug | null
  readonly parseGitHubRepositoryOwner: (repoUrl: string) => string | null
  readonly buildGitHubAppInstallationUrl: (owner: string, appSlug?: string | null) => string
  readonly buildGitHubAppInstallationUrlForRepo: (repoUrl: string, appSlug?: string | null) => string | null
}

export const DEFAULT_GITHUB_APP_SLUG = 'personal-stack-agents'
export const GITHUB_APP_SLUG_ENV_KEY = 'VITE_GITHUB_APP_SLUG'

const GITHUB_BASE_URL = 'https://github.com'
const GITHUB_APP_SLUG_PATTERN = /^[a-z0-9](?:[a-z0-9-]{0,98}[a-z0-9])?$/
const GITHUB_OWNER_PATTERN = /^(?!.*--)[A-Z0-9](?:[A-Z0-9-]{0,37}[A-Z0-9])?$/i
const SCP_LIKE_REPO_URL = /^[^@]+@[^:]+:([^/]+)\/([^/]+?)(?:\.git)?\/?$/
const URL_LIKE_REPO_URL = /^[a-zA-Z]+:\/\/[^/]+\/([^/]+)\/([^/]+?)(?:\.git)?\/?$/

function permission(key: string, access: string, label: string): GitHubAppRequestedPermission {
  return Object.freeze({ key, access, label })
}

export const GITHUB_APP_REQUESTED_PERMISSIONS: ReadonlyArray<GitHubAppRequestedPermission> = Object.freeze([
  permission('contents', 'write', 'Contents'),
  permission('pull_requests', 'write', 'Pull requests'),
  permission('actions', 'write', 'Actions'),
])

export function isValidGitHubAppSlug(slug: string): boolean {
  return GITHUB_APP_SLUG_PATTERN.test(slug.trim())
}

export function resolveGitHubAppSlug(override?: string | null): string {
  const candidate = firstConfiguredSlug(override, import.meta.env[GITHUB_APP_SLUG_ENV_KEY], DEFAULT_GITHUB_APP_SLUG)
  if (!isValidGitHubAppSlug(candidate)) {
    throw new Error(`Invalid GitHub App slug: ${candidate}`)
  }
  return candidate
}

export function isValidGitHubOwner(owner: string): boolean {
  return GITHUB_OWNER_PATTERN.test(owner.trim())
}

export function parseGitHubRepositoryUrl(repoUrl: string): GitHubRepositorySlug | null {
  const trimmed = repoUrl.trim()
  const match = SCP_LIKE_REPO_URL.exec(trimmed) ?? URL_LIKE_REPO_URL.exec(trimmed)
  if (match === null) {
    return null
  }
  const owner = match[1]?.trim()
  const repo = match[2]?.trim()
  if (owner === undefined || repo === undefined || owner === '' || repo === '' || !isValidGitHubOwner(owner)) {
    return null
  }
  return Object.freeze({ owner, repo })
}

export function parseGitHubRepositoryOwner(repoUrl: string): string | null {
  return parseGitHubRepositoryUrl(repoUrl)?.owner ?? null
}

export function buildGitHubAppInstallationUrl(owner: string, appSlug?: string | null): string {
  const trimmedOwner = owner.trim()
  if (!isValidGitHubOwner(trimmedOwner)) {
    throw new Error(`Invalid GitHub owner: ${owner}`)
  }
  const encodedSlug = encodeURIComponent(resolveGitHubAppSlug(appSlug))
  const url = new URL(`${GITHUB_BASE_URL}/apps/${encodedSlug}/installations/new`)
  url.searchParams.set('state', trimmedOwner)
  return url.toString()
}

export function buildGitHubAppInstallationUrlForRepo(repoUrl: string, appSlug?: string | null): string | null {
  const owner = parseGitHubRepositoryOwner(repoUrl)
  if (owner === null) {
    return null
  }
  return buildGitHubAppInstallationUrl(owner, appSlug)
}

function firstConfiguredSlug(
  override: string | null | undefined,
  env: string | boolean | undefined,
  fallback: string,
): string {
  const overrideSlug = override?.trim()
  if (overrideSlug !== undefined && overrideSlug !== '') {
    return overrideSlug
  }
  const envSlug = typeof env === 'string' ? env.trim() : ''
  return envSlug === '' ? fallback : envSlug
}

export const githubAppLinks: GitHubAppLinksApi = Object.freeze({
  requestedPermissions: GITHUB_APP_REQUESTED_PERMISSIONS,
  isValidGitHubAppSlug,
  resolveGitHubAppSlug,
  isValidGitHubOwner,
  parseGitHubRepositoryUrl,
  parseGitHubRepositoryOwner,
  buildGitHubAppInstallationUrl,
  buildGitHubAppInstallationUrlForRepo,
})
