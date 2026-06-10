/**
 * Chat-only session (backend: chat_sessions table; no Pod is
 * provisioned). The session id is the conversation id; messages
 * are appended via the sessions endpoint.
 */
export interface ChatSession {
  id: string
  userId: string
  title: string | null
  status: 'ACTIVE' | 'ARCHIVED'
  createdAt: string
  updatedAt: string
}

export type ChatMessageRole = 'USER' | 'ASSISTANT' | 'SYSTEM'

export interface ChatMessage {
  id: string
  sessionId: string
  role: ChatMessageRole
  body: string
  createdAt: string
  streaming?: boolean
  failed?: boolean
}

export interface ChatSessionDetail {
  session: ChatSession
  messages: ChatMessage[]
}

export interface StartChatSessionInput {
  title?: string
}

export interface AppendChatMessageInput {
  body: string
  role?: ChatMessageRole
}
