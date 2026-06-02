import type { AgentKind, Turn, Workspace, WorkspaceDetail } from '../types'
import { ApiError, useApiWithAuth } from '@personal-stack/vue-common'

function getApi(): ReturnType<typeof useApiWithAuth> {
  return useApiWithAuth({ baseUrl: '/api/v1' })
}

// A freshly-provisioned runner's gateway needs a JVM cold start before
// it is Ready; assistant-api answers start-session with a 503 carrying
// retryAfterSeconds until then. Poll through that window rather than
// surfacing the transient 503 to the user.
const SESSION_START_BUDGET_MS = 180_000
const DEFAULT_RETRY_AFTER_S = 5

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, ms)
  })
}

export async function listWorkspaces(): Promise<Workspace[]> {
  return getApi().get<Workspace[]>('/workspaces')
}

export async function getWorkspace(id: string): Promise<WorkspaceDetail> {
  return getApi().get<WorkspaceDetail>(`/workspaces/${id}`)
}

export type WorkspaceKind = 'REPO_BACKED' | 'SCRATCH' | 'CHAT'

export interface CreateWorkspaceInput {
  name: string
  /**
   * Workspace flavour. `REPO_BACKED` clones a repo (needs
   * `repositoryId` or the legacy `repoUrl`). `SCRATCH` spins up a
   * Pod with no clone — useful for ad-hoc shell work.
   */
  kind?: WorkspaceKind
  /**
   * The preferred way to bind a workspace to a repo + its deploy key.
   * When set, the API derives `repoUrl` and `branch` from the
   * repository row.
   */
  repositoryId?: string | null
  /**
   * Optional project context. Set when the workspace was opened from
   * a project's UI; null for ad-hoc / scratch work.
   */
  projectId?: string | null
  /** Override of the repository's default branch. */
  branch?: string | null
  /** Ad-hoc workspaces still take a raw URL. */
  repoUrl?: string | null
  /**
   * @deprecated — prefer `repositoryId`. The server accepts both for
   * the migration window; PR H will drop this once the OpenAPI gate
   * lands.
   */
  githubLinkId?: string | null
}

export async function createWorkspace(input: CreateWorkspaceInput): Promise<Workspace> {
  return getApi().post<Workspace>('/workspaces', input)
}

export async function destroyWorkspace(id: string): Promise<void> {
  return getApi().del(`/workspaces/${id}`)
}

export async function startSession(
  workspaceId: string,
  kind: AgentKind,
  onWaiting?: (retryInSeconds: number) => void,
): Promise<{ sessionId: string }> {
  const deadline = Date.now() + SESSION_START_BUDGET_MS
  for (;;) {
    try {
      return await getApi().post<{ sessionId: string }>(`/workspaces/${workspaceId}/sessions`, { kind })
    } catch (err) {
      if (err instanceof ApiError && err.status === 503 && Date.now() < deadline) {
        const waitSeconds = Math.max(1, err.problem.retryAfterSeconds ?? DEFAULT_RETRY_AFTER_S)
        onWaiting?.(waitSeconds)
        await delay(waitSeconds * 1000)
        continue
      }
      throw err
    }
  }
}

export async function stopSession(workspaceId: string, sessionId: string): Promise<void> {
  return getApi().del(`/workspaces/${workspaceId}/sessions/${sessionId}`)
}

export async function getTurns(workspaceId: string, sessionId: string): Promise<Turn[]> {
  return getApi().get<Turn[]>(`/workspaces/${workspaceId}/sessions/${sessionId}/turns`)
}

export async function sendInput(workspaceId: string, sessionId: string, text: string, enter = true): Promise<void> {
  return getApi().post(`/workspaces/${workspaceId}/sessions/${sessionId}/input`, { text, enter })
}
