import type { ChatMessage, ChatSession, ChatSessionDetail } from '../types'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  appendChatMessage,
  archiveChatSession,
  getChatSession,
  listChatSessions,
  startChatSession,
} from '../services/chatSessionsService'
import { useChatSessionsStore } from '../stores/chatSessions'

vi.mock('../services/chatSessionsService', () => ({
  listChatSessions: vi.fn(),
  getChatSession: vi.fn(),
  startChatSession: vi.fn(),
  appendChatMessage: vi.fn(),
  archiveChatSession: vi.fn(),
}))

const mocked = {
  listChatSessions: vi.mocked(listChatSessions),
  getChatSession: vi.mocked(getChatSession),
  startChatSession: vi.mocked(startChatSession),
  appendChatMessage: vi.mocked(appendChatMessage),
  archiveChatSession: vi.mocked(archiveChatSession),
}

function fakeSession(over: Partial<ChatSession> = {}): ChatSession {
  return {
    id: 'ssssssss-ssss-ssss-ssss-ssssssssssss',
    userId: 'uuuuuuuu-uuuu-uuuu-uuuu-uuuuuuuuuuuu',
    title: 'demo',
    status: 'ACTIVE',
    createdAt: '2026-05-20T10:00:00Z',
    updatedAt: '2026-05-20T10:00:00Z',
    ...over,
  }
}

function fakeMessage(over: Partial<ChatMessage> = {}): ChatMessage {
  return {
    id: 'mmmmmmmm-mmmm-mmmm-mmmm-mmmmmmmmmmmm',
    sessionId: 'ssssssss-ssss-ssss-ssss-ssssssssssss',
    role: 'USER',
    body: 'hello',
    createdAt: '2026-05-20T10:00:01Z',
    ...over,
  }
}

describe('chatSessions store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loadAll populates sessions', async () => {
    mocked.listChatSessions.mockResolvedValue([fakeSession()])
    const store = useChatSessionsStore()
    await store.loadAll()
    expect(store.sessions).toHaveLength(1)
    expect(store.error).toBeNull()
  })

  it('loadAll surfaces + re-throws errors', async () => {
    mocked.listChatSessions.mockRejectedValue(new Error('boom'))
    const store = useChatSessionsStore()
    await expect(store.loadAll()).rejects.toThrow('boom')
    expect(store.error).toBe('boom')
  })

  it('open caches detail and sets active', async () => {
    const detail: ChatSessionDetail = { session: fakeSession(), messages: [fakeMessage()] }
    mocked.getChatSession.mockResolvedValue(detail)
    const store = useChatSessionsStore()
    await store.open(detail.session.id)
    expect(store.activeSessionId).toBe(detail.session.id)
    expect(store.detailById[detail.session.id]).toEqual(detail)
  })

  it('start prepends the new session and seeds an empty detail', async () => {
    const created = fakeSession({ id: 'new' })
    mocked.startChatSession.mockResolvedValue(created)
    const store = useChatSessionsStore()
    store.sessions = [fakeSession({ id: 'old' })]
    await store.start({ title: 'new' })
    expect(store.sessions[0]!.id).toBe('new')
    expect(store.activeSessionId).toBe('new')
    expect(store.detailById.new!.messages).toEqual([])
  })

  it('send appends the message to the detail', async () => {
    const detail: ChatSessionDetail = { session: fakeSession(), messages: [] }
    const incoming = fakeMessage({ body: 'pong' })
    mocked.appendChatMessage.mockResolvedValue(incoming)
    const store = useChatSessionsStore()
    store.detailById = { [detail.session.id]: detail }
    await store.send(detail.session.id, { body: 'pong' })
    // Use deep equality — Vue's reactive proxy wraps pushed objects,
    // so reference-equal `toContain` fails on proxy-vs-original.
    expect(store.detailById[detail.session.id]!.messages).toContainEqual(incoming)
  })

  it('archive removes from sessions + detail, clears active', async () => {
    mocked.archiveChatSession.mockResolvedValue()
    const store = useChatSessionsStore()
    const session = fakeSession()
    store.sessions = [session]
    store.detailById = { [session.id]: { session, messages: [] } }
    store.activeSessionId = session.id
    await store.archive(session.id)
    expect(store.sessions).toHaveLength(0)
    expect(store.detailById[session.id]).toBeUndefined()
    expect(store.activeSessionId).toBeNull()
  })
})
