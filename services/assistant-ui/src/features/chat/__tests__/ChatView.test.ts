import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useConversationStore } from '@/stores/conversation'
import ChatView from '../views/ChatView.vue'

vi.mock('@/stores/conversation', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/stores/conversation')>()
  return actual
})

vi.mock('@personal-stack/vue-common', () => ({
  useAuth: () => ({
    getAccessToken: () => 'fake-token',
    isAuthenticated: { value: true },
    user: { value: { id: '1', username: 'test', email: '', role: 'USER' } },
  }),
  useApiWithAuth: () => ({
    get: vi.fn().mockResolvedValue([]),
    post: vi.fn().mockResolvedValue({}),
    del: vi.fn().mockResolvedValue(undefined),
  }),
  useTheme: () => ({ isDark: { value: true }, toggle: vi.fn() }),
}))

describe('chatView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  function mountComponent() {
    const pinia = createPinia()
    setActivePinia(pinia)
    return mount(ChatView, {
      global: {
        plugins: [pinia],
        stubs: { ChatInput: true, ConversationList: true },
      },
    })
  }

  it('renders without crashing', () => {
    const wrapper = mountComponent()
    expect(wrapper.exists()).toBe(true)
  })

  it('shows assistant heading', () => {
    const wrapper = mountComponent()
    expect(wrapper.find('h1').exists()).toBe(true)
  })

  it('shows placeholder when no conversation selected', () => {
    const wrapper = mountComponent()
    expect(wrapper.text()).toContain('Select a conversation')
  })

  it('renders messages when conversation is active', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useConversationStore()
    store.$patch({
      activeConversationId: 'conv-1',
      conversations: [
        {
          id: 'conv-1',
          title: 'Test',
          userId: 'u1',
          status: 'ACTIVE',
          createdAt: '2024-01-01',
          updatedAt: '2024-01-01',
        },
      ],
      messages: [
        { id: 'm1', conversationId: 'conv-1', role: 'USER', content: 'Hello', createdAt: '2024-01-01T10:00:00Z' },
        {
          id: 'm2',
          conversationId: 'conv-1',
          role: 'ASSISTANT',
          content: 'Hi there',
          createdAt: '2024-01-01T10:00:01Z',
        },
      ],
    })

    const wrapper = mount(ChatView, {
      global: {
        plugins: [pinia],
        stubs: { ChatInput: true, ConversationList: true },
      },
    })

    expect(wrapper.text()).toContain('Hello')
    expect(wrapper.text()).toContain('Hi there')
  })

  it('shows thinking indicator when sending', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useConversationStore()
    store.$patch({
      activeConversationId: 'conv-1',
      conversations: [
        {
          id: 'conv-1',
          title: 'Test',
          userId: 'u1',
          status: 'ACTIVE',
          createdAt: '2024-01-01',
          updatedAt: '2024-01-01',
        },
      ],
      isSending: true,
    })

    const wrapper = mount(ChatView, {
      global: {
        plugins: [pinia],
        stubs: { ChatInput: true, ConversationList: true },
      },
    })

    expect(wrapper.text()).toContain('Thinking...')
  })

  it('renders ChatInput when conversation is active', () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useConversationStore()
    store.$patch({ activeConversationId: 'conv-1' })

    const wrapper = mount(ChatView, {
      global: {
        plugins: [pinia],
        stubs: { ChatInput: true, ConversationList: true },
      },
    })

    expect(wrapper.findComponent({ name: 'ChatInput' }).exists()).toBe(true)
  })
})
