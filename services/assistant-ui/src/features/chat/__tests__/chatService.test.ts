import { beforeEach, describe, expect, it, vi } from 'vitest'

const mockGet = vi.fn()
const mockPost = vi.fn()
const mockDel = vi.fn()

vi.mock('@personal-stack/vue-common', () => ({
  useApiWithAuth: () => ({
    get: mockGet,
    post: mockPost,
    del: mockDel,
  }),
}))

const { getConversations, createConversation, getMessages, sendMessage, archiveConversation, getConversation } =
  await import('../services/chatService')

describe('chatService', () => {
  beforeEach(() => {
    mockGet.mockReset()
    mockPost.mockReset()
    mockDel.mockReset()
  })

  it('getConversations calls GET /conversations', async () => {
    const conversations = [{ id: '1', title: 'Test' }]
    mockGet.mockResolvedValue(conversations)

    const result = await getConversations()

    expect(mockGet).toHaveBeenCalledWith('/conversations')
    expect(result).toEqual(conversations)
  })

  it('createConversation calls POST /conversations with title', async () => {
    const conversation = { id: '1', title: 'New Chat' }
    mockPost.mockResolvedValue(conversation)

    const result = await createConversation('New Chat')

    expect(mockPost).toHaveBeenCalledWith('/conversations', { title: 'New Chat' })
    expect(result).toEqual(conversation)
  })

  it('getMessages calls GET /conversations/{id}/messages', async () => {
    const messages = [{ id: 'm1', content: 'Hello' }]
    mockGet.mockResolvedValue(messages)

    const result = await getMessages('conv-1')

    expect(mockGet).toHaveBeenCalledWith('/conversations/conv-1/messages')
    expect(result).toEqual(messages)
  })

  it('sendMessage calls POST /conversations/{id}/messages with content', async () => {
    const message = { id: 'm1', content: 'Hi', role: 'USER' }
    mockPost.mockResolvedValue(message)

    const result = await sendMessage('conv-1', 'Hi')

    expect(mockPost).toHaveBeenCalledWith('/conversations/conv-1/messages', {
      content: 'Hi',
      role: 'USER',
    })
    expect(result).toEqual(message)
  })

  it('archiveConversation calls DEL /conversations/{id}', async () => {
    mockDel.mockResolvedValue(undefined)

    await archiveConversation('conv-1')

    expect(mockDel).toHaveBeenCalledWith('/conversations/conv-1')
  })

  it('getConversation calls GET /conversations/{id}', async () => {
    const conversation = { id: 'conv-1', title: 'Chat' }
    mockGet.mockResolvedValue(conversation)

    const result = await getConversation('conv-1')

    expect(mockGet).toHaveBeenCalledWith('/conversations/conv-1')
    expect(result).toEqual(conversation)
  })
})
