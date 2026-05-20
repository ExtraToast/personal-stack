// Public surface for the sessions feature.
export { default as ChatTab } from './components/ChatTab.vue'
export { default as CreateWorkspaceWizard } from './components/CreateWorkspaceWizard.vue'
export { default as ScratchTab } from './components/ScratchTab.vue'
export { default as WorkspaceTab } from './components/WorkspaceTab.vue'
export { useChatSessionsStore } from './stores/chatSessions'
export type {
  AppendChatMessageInput,
  ChatMessage,
  ChatMessageRole,
  ChatSession,
  ChatSessionDetail,
  StartChatSessionInput,
} from './types'
export { default as SessionsView } from './views/SessionsView.vue'
