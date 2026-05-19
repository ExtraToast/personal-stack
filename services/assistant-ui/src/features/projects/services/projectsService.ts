import type { GithubLink, Project, ProjectDetail } from '../types'
import { useApiWithAuth } from '@personal-stack/vue-common'

function getApi(): ReturnType<typeof useApiWithAuth> {
  return useApiWithAuth({ baseUrl: '/api/v1' })
}

export async function listProjects(): Promise<Project[]> {
  return getApi().get<Project[]>('/projects')
}

export async function getProject(id: string): Promise<ProjectDetail> {
  return getApi().get<ProjectDetail>(`/projects/${id}`)
}

export async function createProject(input: { name: string; slug: string; description?: string }): Promise<Project> {
  return getApi().post<Project>('/projects', input)
}

export async function addLink(
  projectId: string,
  input: {
    name: string
    repoUrl: string
    defaultBranch?: string
  },
): Promise<GithubLink> {
  return getApi().post<GithubLink>(`/projects/${projectId}/links`, input)
}

export async function removeLink(projectId: string, linkId: string): Promise<void> {
  return getApi().del(`/projects/${projectId}/links/${linkId}`)
}

export async function attachKey(
  projectId: string,
  linkId: string,
  input: {
    privateKeyOpenssh: string
    publicKeyOpenssh: string
    knownHosts?: string
  },
): Promise<void> {
  return getApi().post(`/projects/${projectId}/links/${linkId}/key`, input)
}

/**
 * Returns the markdown body of the deploy-key setup guide as a
 * string. The assistant-api endpoint returns `text/markdown`; we
 * rely on the helper's text-fallback rather than the JSON path.
 */
export async function getSetupGuide(projectId: string, linkId: string): Promise<string> {
  // useApiWithAuth's `get` defaults to JSON; for a text body the
  // simplest fallback is a raw fetch that reuses the same cookie-
  // based session (forward-auth runs in front of the API).
  const resp = await fetch(`/api/v1/projects/${projectId}/links/${linkId}/setup-guide`, {
    credentials: 'include',
    headers: { Accept: 'text/markdown,text/plain' },
  })
  if (!resp.ok) throw new Error(`setup-guide fetch failed: ${resp.status}`)
  return await resp.text()
}
