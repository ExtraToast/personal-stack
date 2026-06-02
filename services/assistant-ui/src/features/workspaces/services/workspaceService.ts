import type { AgentKind, Turn, Workspace, WorkspaceDetail } from '../types'
import { ApiError, useApiWithAuth } from '@personal-stack/vue-common'

function getApi(): ReturnType<typeof useApiWithAuth> {
  return useApiWithAuth({ baseUrl: '/api/v1' })
}

const sleep = (ms: number): Promise<void> => new Promise((resolve) => setTimeout(resolve, ms))

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

export interface StartSessionOptions {
  /** Total budget to keep retrying while the runner cold-starts. */
  maxWaitMs?: number
  /** Invoked on each "runner not ready yet" 503, with the server's retry hint. */
  onWaiting?: (retryAfterSeconds: number) => void
}

// A fresh runner Pod cold-starts a JVM gateway (schedule + image + boot,
// ~15-30s), during which POST /sessions returns 503 with retryAfterSeconds.
// That is the runner not being ready yet, not a failure — honour the hint
// and retry until it is up rather than surfacing the 503 to the operator.
export async function startSession(
  workspaceId: string,
  kind: AgentKind,
  opts: StartSessionOptions = {},
): Promise<{ sessionId: string }> {
  const deadline = Date.now() + (opts.maxWaitMs ?? 120_000)
  for (;;) {
    try {
      return await getApi().post<{ sessionId: string }>(`/workspaces/${workspaceId}/sessions`, { kind })
    } catch (e) {
      const retryAfter = e instanceof ApiError && e.status === 503 ? (e.problem.retryAfterSeconds ?? 0) : 0
      if (retryAfter <= 0 || Date.now() + retryAfter * 1000 > deadline) throw e
      opts.onWaiting?.(retryAfter)
      await sleep(retryAfter * 1000)
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
