import type { Conversation, Message } from '../types'
import { useApiWithAuth } from '@private-stack/vue-common'

function getApi(): ReturnType<typeof useApiWithAuth> {
  return useApiWithAuth({ baseUrl: '/api/v1' })
}

export async function getConversations(): Promise<Conversation[]> {
  return getApi().get<Conversation[]>('/conversations')
}

export async function getConversation(id: string): Promise<Conversation> {
  return getApi().get<Conversation>(`/conversations/${id}`)
}

export async function createConversation(title: string): Promise<Conversation> {
  return getApi().post<Conversation>('/conversations', { title })
}

export async function archiveConversation(id: string): Promise<void> {
  return getApi().del(`/conversations/${id}`)
}

export async function getMessages(conversationId: string): Promise<Message[]> {
  return getApi().get<Message[]>(`/conversations/${conversationId}/messages`)
}

export async function sendMessage(conversationId: string, content: string): Promise<Message> {
  return getApi().post<Message>(`/conversations/${conversationId}/messages`, {
    content,
    role: 'USER',
  })
}
