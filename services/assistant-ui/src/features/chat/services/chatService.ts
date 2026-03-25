import { useApi } from '@private-stack/vue-common'
import type { Message, Conversation } from '../types'

const api = useApi()

export async function sendMessage(conversationId: string, content: string): Promise<Message> {
  return api.post<Message>(`/conversations/${conversationId}/messages`, { content })
}

export async function getConversations(): Promise<Conversation[]> {
  return api.get<Conversation[]>('/conversations')
}

export async function getConversation(id: string): Promise<Conversation> {
  return api.get<Conversation>(`/conversations/${id}`)
}

export async function createConversation(title: string): Promise<Conversation> {
  return api.post<Conversation>('/conversations', { title })
}
