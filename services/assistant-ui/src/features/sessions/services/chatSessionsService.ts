import type {
  AppendChatMessageInput,
  ChatMessage,
  ChatSession,
  ChatSessionDetail,
  StartChatSessionInput,
} from '../types'
import { useApiWithAuth } from '@/lib/vueWebCommons'

function api(): ReturnType<typeof useApiWithAuth> {
  return useApiWithAuth({ baseUrl: '/api/v1' })
}

export async function listChatSessions(): Promise<ChatSession[]> {
  return api().get<ChatSession[]>('/chat-sessions')
}

export async function getChatSession(id: string): Promise<ChatSessionDetail> {
  return api().get<ChatSessionDetail>(`/chat-sessions/${id}`)
}

export async function startChatSession(input: StartChatSessionInput = {}): Promise<ChatSession> {
  return api().post<ChatSession>('/chat-sessions', input)
}

export async function appendChatMessage(id: string, input: AppendChatMessageInput): Promise<ChatMessage> {
  return api().post<ChatMessage>(`/chat-sessions/${id}/messages`, input)
}

export async function archiveChatSession(id: string): Promise<void> {
  await api().del(`/chat-sessions/${id}`)
}
