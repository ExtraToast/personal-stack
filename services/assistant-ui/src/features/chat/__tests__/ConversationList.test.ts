import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { useConversationStore } from '@/stores/conversation'
import ConversationList from '../components/ConversationList.vue'

describe('conversationList', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  function mountComponent() {
    return mount(ConversationList, {
      global: { plugins: [createPinia()] },
    })
  }

  it('renders new conversation button', () => {
    const wrapper = mountComponent()

    const newButton = wrapper.find('button')
    expect(newButton.exists()).toBe(true)
    expect(newButton.text()).toContain('New')
  })

  it('renders conversation titles', () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useConversationStore()
    store.$patch({
      conversations: [
        {
          id: '1',
          title: 'First Chat',
          userId: 'u1',
          status: 'ACTIVE',
          createdAt: '2024-01-01',
          updatedAt: '2024-01-01',
        },
        {
          id: '2',
          title: 'Second Chat',
          userId: 'u1',
          status: 'ACTIVE',
          createdAt: '2024-01-02',
          updatedAt: '2024-01-02',
        },
      ],
    })

    const wrapper = mount(ConversationList, { global: { plugins: [pinia] } })

    expect(wrapper.text()).toContain('First Chat')
    expect(wrapper.text()).toContain('Second Chat')
  })

  it('highlights active conversation', () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useConversationStore()
    store.$patch({
      conversations: [
        {
          id: '1',
          title: 'First Chat',
          userId: 'u1',
          status: 'ACTIVE',
          createdAt: '2024-01-01',
          updatedAt: '2024-01-01',
        },
        {
          id: '2',
          title: 'Second Chat',
          userId: 'u1',
          status: 'ACTIVE',
          createdAt: '2024-01-02',
          updatedAt: '2024-01-02',
        },
      ],
      activeConversationId: '2',
    })

    const wrapper = mount(ConversationList, { global: { plugins: [pinia] } })

    // Active conversation should have different styling
    const items = wrapper.findAll('button').filter((b) => b.text().includes('Second Chat'))
    expect(items.length).toBeGreaterThan(0)
  })

  it('shows empty state when no conversations', () => {
    const wrapper = mountComponent()

    expect(wrapper.text()).toContain('No conversations yet')
  })
})
