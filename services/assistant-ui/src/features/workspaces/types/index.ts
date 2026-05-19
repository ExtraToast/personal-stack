export type WorkspaceStatus =
  | 'PENDING'
  | 'STARTING'
  | 'READY'
  | 'IDLE'
  | 'FAILED'
  | 'DESTROYED'

export interface Workspace {
  id: string
  name: string
  repoUrl: string | null
  branch: string | null
  podName: string | null
  gatewayEndpoint: string | null
  status: WorkspaceStatus
  githubLinkId: string | null
  createdAt: string
  updatedAt: string
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

export interface WorkspaceDetail {
  workspace: Workspace
  sessions: AgentSession[]
}
