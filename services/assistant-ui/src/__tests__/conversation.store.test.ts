import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/features/chat/services/chatService', () => ({
  getConversations: vi.fn(),
  createConversation: vi.fn(),
  getMessages: vi.fn(),
  sendMessage: vi.fn(),
  archiveConversation: vi.fn(),
}))

const chatService = await import('@/features/chat/services/chatService')
const { useConversationStore } = await import('@/stores/conversation')

const mockedGetConversations = vi.mocked(chatService.getConversations)
const mockedCreateConversation = vi.mocked(chatService.createConversation)
const mockedGetMessages = vi.mocked(chatService.getMessages)
const mockedSendMessage = vi.mocked(chatService.sendMessage)
const mockedArchiveConversation = vi.mocked(chatService.archiveConversation)

describe('conversation store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('initial state has empty conversations and no active conversation', () => {
    const store = useConversationStore()

    expect(store.conversations).toEqual([])
    expect(store.activeConversationId).toBeNull()
    expect(store.messages).toEqual([])
    expect(store.isLoading).toBe(false)
    expect(store.isSending).toBe(false)
    expect(store.error).toBeNull()
  })

  it('loadConversations fetches and sets conversations', async () => {
    const conversations = [
      {
        id: '1',
        title: 'Chat 1',
        userId: 'u1',
        status: 'ACTIVE' as const,
        createdAt: '2024-01-01',
        updatedAt: '2024-01-01',
      },
      {
        id: '2',
        title: 'Chat 2',
        userId: 'u1',
        status: 'ACTIVE' as const,
        createdAt: '2024-01-02',
        updatedAt: '2024-01-02',
      },
    ]
    mockedGetConversations.mockResolvedValue(conversations)

    const store = useConversationStore()
    await store.loadConversations()

    expect(mockedGetConversations).toHaveBeenCalled()
    expect(store.conversations).toEqual(conversations)
    expect(store.isLoading).toBe(false)
  })

  it('selectConversation sets active id and loads messages', async () => {
    const messages = [
      { id: 'm1', conversationId: 'conv-1', role: 'USER' as const, content: 'Hello', createdAt: '2024-01-01' },
    ]
    mockedGetMessages.mockResolvedValue(messages)

    const store = useConversationStore()
    await store.selectConversation('conv-1')

    expect(store.activeConversationId).toBe('conv-1')
    expect(mockedGetMessages).toHaveBeenCalledWith('conv-1')
    expect(store.messages).toEqual(messages)
  })

  it('startConversation creates and selects new conversation', async () => {
    const newConv = {
      id: 'new-1',
      title: 'New Chat',
      userId: 'u1',
      status: 'ACTIVE' as const,
      createdAt: '2024-01-01',
      updatedAt: '2024-01-01',
    }
    mockedCreateConversation.mockResolvedValue(newConv)
    mockedGetMessages.mockResolvedValue([])

    const store = useConversationStore()
    await store.startConversation('New Chat')

    expect(mockedCreateConversation).toHaveBeenCalledWith('New Chat')
    expect(store.conversations[0]).toEqual(newConv)
    expect(store.activeConversationId).toBe('new-1')
  })

  it('send adds optimistic message and calls API', async () => {
    const savedMessage = {
      id: 'server-1',
      conversationId: 'conv-1',
      role: 'USER' as const,
      content: 'Hi',
      createdAt: '2024-01-01',
    }
    mockedSendMessage.mockResolvedValue(savedMessage)

    const store = useConversationStore()
    store.activeConversationId = 'conv-1'

    await store.send('Hi')

    expect(mockedSendMessage).toHaveBeenCalledWith('conv-1', 'Hi')
    expect(store.messages).toHaveLength(1)
    expect(store.messages[0].id).toBe('server-1')
    expect(store.isSending).toBe(false)
  })

  it('send removes optimistic message on failure', async () => {
    mockedSendMessage.mockRejectedValue(new Error('Network error'))

    const store = useConversationStore()
    store.activeConversationId = 'conv-1'

    await store.send('Hi')

    expect(store.messages).toHaveLength(0)
    expect(store.error).toBe('Failed to send message')
    expect(store.isSending).toBe(false)
  })

  it('archive removes conversation from list', async () => {
    mockedArchiveConversation.mockResolvedValue(undefined)

    const store = useConversationStore()
    store.conversations = [
      {
        id: 'conv-1',
        title: 'Chat 1',
        userId: 'u1',
        status: 'ACTIVE',
        createdAt: '2024-01-01',
        updatedAt: '2024-01-01',
      },
      {
        id: 'conv-2',
        title: 'Chat 2',
        userId: 'u1',
        status: 'ACTIVE',
        createdAt: '2024-01-02',
        updatedAt: '2024-01-02',
      },
    ]
    store.activeConversationId = 'conv-1'

    await store.archive('conv-1')

    expect(mockedArchiveConversation).toHaveBeenCalledWith('conv-1')
    expect(store.conversations).toHaveLength(1)
    expect(store.conversations[0].id).toBe('conv-2')
    expect(store.activeConversationId).toBeNull()
    expect(store.messages).toEqual([])
  })

  it('activeConversation returns the selected conversation', () => {
    const store = useConversationStore()
    store.conversations = [
      {
        id: 'conv-1',
        title: 'Chat 1',
        userId: 'u1',
        status: 'ACTIVE',
        createdAt: '2024-01-01',
        updatedAt: '2024-01-01',
      },
      {
        id: 'conv-2',
        title: 'Chat 2',
        userId: 'u1',
        status: 'ACTIVE',
        createdAt: '2024-01-02',
        updatedAt: '2024-01-02',
      },
    ]
    store.activeConversationId = 'conv-2'

    const active = store.activeConversation()

    expect(active?.id).toBe('conv-2')
    expect(active?.title).toBe('Chat 2')
  })
})
