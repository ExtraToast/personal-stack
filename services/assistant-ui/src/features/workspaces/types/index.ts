export type WorkspaceStatus = 'PENDING' | 'STARTING' | 'READY' | 'IDLE' | 'FAILED' | 'DESTROYED'

/**
 * Workspace flavour. The shape PR E introduced so the redesigned UI
 * can distinguish a real cloned repo from a scratch sandbox or a
 * chat-only session.
 *
 * - `REPO_BACKED`: Pod boots, clones `repoUrl` / `branch` from the
 *   bound Repository's deploy key. The default.
 * - `SCRATCH`: Pod boots without a clone. The agent has a shell + the
 *   CLIs but no working tree.
 * - `CHAT`: no Pod at all. The "workspace" is purely a placeholder so
 *   sessions can reference it; chat traffic flows through
 *   `chat_sessions` separately.
 */
export type WorkspaceKind = 'REPO_BACKED' | 'SCRATCH' | 'CHAT'

export interface Workspace {
  id: string
  name: string
  repoUrl: string | null
  branch: string | null
  podName: string | null
  gatewayEndpoint: string | null
  status: WorkspaceStatus
  kind: WorkspaceKind
  projectId: string | null
  repositoryId: string | null
  /**
   * @deprecated Legacy GithubLink id. Surfaces during the M:N
   * migration window so existing rows keep rendering; net-new
   * callers should use `repositoryId`.
   */
  githubLinkId: string | null
  createdAt: string
  updatedAt: string
}

export interface WorkspaceRepositoryVerification {
  read?: boolean | null
  write?: boolean | null
  defaultBranchProtected?: boolean | null
  checkedAt?: string | null
  messages: string[]
}

export interface WorkspaceRepository {
  id: string
  name: string
  repoUrl: string
  defaultBranch: string
  vaultKeyPath: string
  deployKeyFingerprint?: string | null
  deployKeyAddedAt?: string | null
  createdAt: string
  updatedAt: string
  verification?: WorkspaceRepositoryVerification | null
  isPrimary: boolean
  attachedAt: string
}

export interface WorkspaceDetailWorkspace extends Workspace {
  repositories?: WorkspaceRepository[]
}

export type AgentKind = 'CLAUDE' | 'CODEX' | 'SHELL'

export type AgentSessionStatus = 'STARTING' | 'RUNNING' | 'STOPPED' | 'FAILED'

export interface AgentSession {
  id: string
  workspaceId: string
  kind: AgentKind
  gatewayAgentId: string | null
  status: AgentSessionStatus
  createdAt: string
  updatedAt: string
}

export type TurnRole = 'USER' | 'AGENT' | 'SYSTEM'

export interface Turn {
  id: string
  sessionId: string
  role: TurnRole
  body: string
  createdAt: string
}

export interface StagedInput {
  path: string
  bytes: number
  name: string
}

export interface WorkspaceDetail {
  workspace: WorkspaceDetailWorkspace
  sessions: AgentSession[]
}
