import type { Conversation, Message } from '@/features/chat/types'
import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  archiveConversation,
  createConversation,
  getConversations,
  getMessages,
  sendMessage,
} from '@/features/chat/services/chatService'

export const useConversationStore = defineStore('conversation', () => {
  const conversations = ref<Conversation[]>([])
  const activeConversationId = ref<string | null>(null)
  const messages = ref<Message[]>([])
  const isLoading = ref(false)
  const isSending = ref(false)
  const error = ref<string | null>(null)

  function activeConversation(): Conversation | undefined {
    return conversations.value.find((c) => c.id === activeConversationId.value)
  }

  async function loadConversations(): Promise<void> {
    isLoading.value = true
    error.value = null
    try {
      conversations.value = await getConversations()
    } catch {
      error.value = 'Failed to load conversations'
    } finally {
      isLoading.value = false
    }
  }

  async function selectConversation(id: string): Promise<void> {
    activeConversationId.value = id
    isLoading.value = true
    error.value = null
    try {
      messages.value = await getMessages(id)
    } catch {
      error.value = 'Failed to load messages'
    } finally {
      isLoading.value = false
    }
  }

  async function startConversation(title: string): Promise<void> {
    const conversation = await createConversation(title)
    conversations.value.unshift(conversation)
    await selectConversation(conversation.id)
  }

  async function send(content: string): Promise<void> {
    const convId = activeConversationId.value
    if (!convId) return

    // Optimistic local message
    const optimistic: Message = {
      id: crypto.randomUUID(),
      conversationId: convId,
      role: 'USER',
      content,
      createdAt: new Date().toISOString(),
    }
    messages.value.push(optimistic)
    isSending.value = true

    try {
      const saved = await sendMessage(convId, content)
      // Replace optimistic with server response
      const idx = messages.value.findIndex((m) => m.id === optimistic.id)
      if (idx !== -1) messages.value[idx] = saved
    } catch {
      // Remove optimistic on error
      messages.value = messages.value.filter((m) => m.id !== optimistic.id)
      error.value = 'Failed to send message'
    } finally {
      isSending.value = false
    }
  }

  async function archive(conversationId: string): Promise<void> {
    await archiveConversation(conversationId)
    conversations.value = conversations.value.filter((c) => c.id !== conversationId)
    if (activeConversationId.value === conversationId) {
      activeConversationId.value = null
      messages.value = []
    }
  }

  return {
    conversations,
    activeConversationId,
    messages,
    isLoading,
    isSending,
    error,
    activeConversation,
    loadConversations,
    selectConversation,
    startConversation,
    send,
    archive,
  }
})
