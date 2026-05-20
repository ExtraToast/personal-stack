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
} from '../services/chatSessionsService'

export const useChatSessionsStore = defineStore('chatSessions', () => {
  const sessions = ref<ChatSession[]>([])
  const activeSessionId = ref<string | null>(null)
  const detailById = ref<Record<string, ChatSessionDetail>>({})
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

  async function archive(id: string): Promise<void> {
    await archiveApi(id)
    sessions.value = sessions.value.filter((s) => s.id !== id)
    delete detailById.value[id]
    if (activeSessionId.value === id) activeSessionId.value = null
  }

  return {
    sessions,
    activeSessionId,
    detailById,
    isLoading,
    error,
    loadAll,
    open,
    start,
    send,
    archive,
  }
})
