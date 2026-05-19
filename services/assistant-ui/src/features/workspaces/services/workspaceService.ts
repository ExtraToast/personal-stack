import type {
  AgentKind,
  AgentSession,
  Turn,
  Workspace,
  WorkspaceDetail,
} from '../types'
import { useApiWithAuth } from '@personal-stack/vue-common'

function getApi(): ReturnType<typeof useApiWithAuth> {
  return useApiWithAuth({ baseUrl: '/api/v1' })
}

export async function listWorkspaces(): Promise<Workspace[]> {
  return getApi().get<Workspace[]>('/workspaces')
}

export async function getWorkspace(id: string): Promise<WorkspaceDetail> {
  return getApi().get<WorkspaceDetail>(`/workspaces/${id}`)
}

export async function createWorkspace(input: {
  name: string
  repoUrl?: string | null
  branch?: string | null
  githubLinkId?: string | null
}): Promise<Workspace> {
  return getApi().post<Workspace>('/workspaces', input)
}

export async function destroyWorkspace(id: string): Promise<void> {
  return getApi().del(`/workspaces/${id}`)
}

export async function startSession(
  workspaceId: string,
  kind: AgentKind,
): Promise<{ sessionId: string }> {
  return getApi().post<{ sessionId: string }>(
    `/workspaces/${workspaceId}/sessions`,
    { kind },
  )
}

export async function stopSession(workspaceId: string, sessionId: string): Promise<void> {
  return getApi().del(`/workspaces/${workspaceId}/sessions/${sessionId}`)
}

export async function getTurns(
  workspaceId: string,
  sessionId: string,
): Promise<Turn[]> {
  return getApi().get<Turn[]>(
    `/workspaces/${workspaceId}/sessions/${sessionId}/turns`,
  )
}

export async function sendInput(
  workspaceId: string,
  sessionId: string,
  text: string,
  enter = true,
): Promise<void> {
  return getApi().post(
    `/workspaces/${workspaceId}/sessions/${sessionId}/input`,
    { text, enter },
  )
}
