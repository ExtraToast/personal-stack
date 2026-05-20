// Public surface of the workspaces feature. Other features must
// import via this barrel rather than reaching into types/stores
// directly — dependency-cruiser's `no-cross-feature-deep-import`
// rule enforces it.
export { useWorkspacesStore } from './stores/workspaces'
export type {
  AgentKind,
  AgentSession,
  AgentSessionStatus,
  Turn,
  TurnRole,
  Workspace,
  WorkspaceDetail,
  WorkspaceKind,
  WorkspaceStatus,
} from './types'
