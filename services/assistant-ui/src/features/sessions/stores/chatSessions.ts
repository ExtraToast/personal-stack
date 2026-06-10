import type {
  AppendChatMessageInput,
  ChatMessage,
  ChatSession,
  ChatSessionDetail,
  StartChatSessionInput,
} from '../types'
import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  appendChatMessage as appendApi,
  archiveChatSession as archiveApi,
  getChatSession as getApi,
  listChatSessions as listApi,
  startChatSession as startApi,
  streamChatAnswer,
} from '../services/chatSessionsService'

let localMessageCounter = 0

interface RetryPrompt {
  body: string
}

export const useChatSessionsStore = defineStore('chatSessions', () => {
  const sessions = ref<ChatSession[]>([])
  const activeSessionId = ref<string | null>(null)
  const detailById = ref<Record<string, ChatSessionDetail>>({})
  const lastFailedById = ref<Record<string, RetryPrompt>>({})
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  async function loadAll(): Promise<void> {
    isLoading.value = true
    error.value = null
    try {
      sessions.value = await listApi()
    } catch (e) {
      error.value = e instanceof Error ? e.message : String(e)
      throw e
    } finally {
      isLoading.value = false
    }
  }

  async function open(id: string): Promise<ChatSessionDetail> {
    const detail = await getApi(id)
    detailById.value[id] = detail
    activeSessionId.value = id
    // Keep the list row in sync with the detail's session.
    const idx = sessions.value.findIndex((s) => s.id === id)
    if (idx >= 0) sessions.value[idx] = detail.session
    return detail
  }

  async function start(input: StartChatSessionInput = {}): Promise<ChatSession> {
    const created = await startApi(input)
    sessions.value = [created, ...sessions.value]
    activeSessionId.value = created.id
    detailById.value[created.id] = { session: created, messages: [] }
    return created
  }

  async function send(id: string, input: AppendChatMessageInput): Promise<ChatMessage> {
    const message = await appendApi(id, input)
    const detail = detailById.value[id]
    if (detail) detail.messages = [...detail.messages, message]
    return message
  }

  async function sendStreaming(id: string, body: string): Promise<void> {
    const detail = detailById.value[id]
    if (!detail) return

    const createdAt = new Date().toISOString()
    const localId = `local-${++localMessageCounter}`
    const streamId = `stream-${++localMessageCounter}`
    const placeholder: ChatMessage = {
      id: streamId,
      sessionId: id,
      role: 'ASSISTANT',
      body: '',
      createdAt,
      streaming: true,
    }
    detail.messages = [
      ...detail.messages,
      {
        id: localId,
        sessionId: id,
        role: 'USER',
        body,
        createdAt,
      },
      placeholder,
    ]
    const streamMessage = detail.messages[detail.messages.length - 1]!
    delete lastFailedById.value[id]

    const markFailed = (): void => {
      streamMessage.streaming = false
      streamMessage.failed = true
      lastFailedById.value[id] = { body }
    }

    try {
      await streamChatAnswer(id, body, {
        onChunk(text) {
          streamMessage.body += text
        },
        onDone(messageId) {
          streamMessage.id = messageId
          streamMessage.streaming = false
        },
        onError: markFailed,
      })
    } catch {
      markFailed()
    }
  }

  async function retryLast(id: string): Promise<void> {
    const retry = lastFailedById.value[id]
    if (!retry) return
    await sendStreaming(id, retry.body)
  }

  async function archive(id: string): Promise<void> {
    await archiveApi(id)
    sessions.value = sessions.value.filter((s) => s.id !== id)
    delete detailById.value[id]
    delete lastFailedById.value[id]
    if (activeSessionId.value === id) activeSessionId.value = null
  }

  return {
    sessions,
    activeSessionId,
    detailById,
    lastFailedById,
    isLoading,
    error,
    loadAll,
    open,
    start,
    send,
    sendStreaming,
    retryLast,
    archive,
  }
})
