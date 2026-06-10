import type {
  AppendChatMessageInput,
  ChatMessage,
  ChatSession,
  ChatSessionDetail,
  StartChatSessionInput,
} from '../types'
import { cookieCsrfTokenSource, useApiWithAuth } from '@/lib/vueWebCommons'

export interface ChatStreamHandlers {
  onChunk: (text: string) => void
  onDone: (messageId: string) => void
  onError: (message: string) => void
}

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

export async function streamChatAnswer(
  id: string,
  body: string,
  h: ChatStreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const token = typeof document !== 'undefined' ? cookieCsrfTokenSource('XSRF-TOKEN', document)() : null
  try {
    const res = await fetch(`/api/v1/chat-sessions/${id}/messages/stream`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { 'X-XSRF-TOKEN': token } : {}),
      },
      body: JSON.stringify({ body }),
      signal: signal ?? null,
    })

    if (!res.ok || !res.body) {
      h.onError(`Chat stream failed (${res.status})`)
      return
    }

    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      buffer = processFrames(buffer, h)
    }

    buffer += decoder.decode()
    if (buffer.trim()) {
      processFrame(buffer, h)
    }
  } catch (e) {
    h.onError(e instanceof Error ? e.message : String(e))
  }
}

export async function archiveChatSession(id: string): Promise<void> {
  await api().del(`/chat-sessions/${id}`)
}

function processFrames(buffer: string, h: ChatStreamHandlers): string {
  const frames = buffer.split(/\r?\n\r?\n/)
  const partial = frames.pop() ?? ''
  for (const frame of frames) {
    processFrame(frame, h)
  }
  return partial
}

function processFrame(frame: string, h: ChatStreamHandlers): void {
  const lines = frame.split(/\r?\n/)
  const eventLine = lines.find((line) => line.startsWith('event:'))
  const dataLine = lines.find((line) => line.startsWith('data:'))
  if (!eventLine || !dataLine) return

  const event = eventLine.slice('event:'.length).trim()
  const rawData = dataLine.slice('data:'.length).trim()
  if (!rawData) return

  try {
    const data: unknown = JSON.parse(rawData)
    if (event === 'chunk') {
      const text = field(data, 'text')
      if (typeof text === 'string') h.onChunk(text)
    } else if (event === 'done') {
      const messageId = field(data, 'messageId')
      if (typeof messageId === 'string') h.onDone(messageId)
    } else if (event === 'error') {
      const message = field(data, 'message')
      h.onError(typeof message === 'string' ? message : 'Chat stream failed')
    }
  } catch {
    h.onError('Could not parse chat stream event')
  }
}

// Reads one property off a parsed-JSON value without trusting its shape.
function field(data: unknown, key: string): unknown {
  if (!data || typeof data !== 'object' || !(key in data)) return undefined
  return (data as Record<string, unknown>)[key] // eslint-disable-line ts/consistent-type-assertions
}
