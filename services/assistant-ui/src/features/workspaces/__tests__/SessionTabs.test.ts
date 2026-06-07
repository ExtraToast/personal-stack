import type { AgentSession } from '../types'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import SessionTabs from '../components/SessionTabs.vue'
import { useSessionLabelsStore } from '../stores/sessionLabels'

function fakeSession(over: Partial<AgentSession> = {}): AgentSession {
  return {
    id: 'aaaaaaaa-1111-2222-3333-444444444444',
    workspaceId: 'ws-1',
    kind: 'CLAUDE',
    gatewayAgentId: 'g1',
    status: 'RUNNING',
    createdAt: '2026-06-03T10:00:00Z',
    updatedAt: '2026-06-03T10:00:00Z',
    ...over,
  }
}

describe('sessionTabs', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('shows the 8-char id by default and a custom label when one is set', () => {
    const labels = useSessionLabelsStore()
    labels.rename('aaaaaaaa-1111-2222-3333-444444444444', 'API tab')
    const wrapper = mount(SessionTabs, {
      props: { sessions: [fakeSession()], activeId: null },
    })
    expect(wrapper.text()).toContain('API tab')
    expect(wrapper.text()).not.toContain('aaaaaaaa')
  })

  it('right-click opens an inline editor; Enter commits and persists the label', async () => {
    const wrapper = mount(SessionTabs, {
      props: { sessions: [fakeSession()], activeId: null },
    })
    expect(wrapper.find('[data-testid="session-tab-rename"]').exists()).toBe(false)

    await wrapper.find('button').trigger('contextmenu')
    const input = wrapper.find('[data-testid="session-tab-rename"]')
    expect(input.exists()).toBe(true)

    await input.setValue('renamed')
    await input.trigger('keydown.enter')

    expect(useSessionLabelsStore().labelFor('aaaaaaaa-1111-2222-3333-444444444444')).toBe('renamed')
    expect(wrapper.find('[data-testid="session-tab-rename"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('renamed')
  })

  it('escape cancels the edit without changing the label', async () => {
    const labels = useSessionLabelsStore()
    labels.rename('aaaaaaaa-1111-2222-3333-444444444444', 'original')
    const wrapper = mount(SessionTabs, {
      props: { sessions: [fakeSession()], activeId: null },
    })
    await wrapper.find('button').trigger('contextmenu')
    const input = wrapper.find('[data-testid="session-tab-rename"]')
    await input.setValue('discarded')
    await input.trigger('keydown.esc')

    expect(labels.labelFor('aaaaaaaa-1111-2222-3333-444444444444')).toBe('original')
    expect(wrapper.text()).toContain('original')
  })

  it('does not emit select while typing in the rename input', async () => {
    const wrapper = mount(SessionTabs, {
      props: { sessions: [fakeSession()], activeId: null },
    })
    await wrapper.find('button').trigger('contextmenu')
    await wrapper.find('[data-testid="session-tab-rename"]').trigger('click')
    expect(wrapper.emitted('select')).toBeUndefined()
  })

  it('renders vertical session controls without nesting the stop action in the selector', async () => {
    const session = fakeSession()
    const wrapper = mount(SessionTabs, {
      props: { sessions: [session], activeId: session.id, orientation: 'vertical' },
    })

    await wrapper.get(`[data-testid="session-tab-${session.id}"]`).trigger('click')
    await wrapper.get(`[data-testid="session-tab-stop-${session.id}"]`).trigger('click')

    expect(wrapper.get('[data-testid="session-tabs"]').attributes('aria-label')).toBe('Agent sessions')
    expect(wrapper.emitted('select')).toEqual([[session.id]])
    expect(wrapper.emitted('stop')).toEqual([[session.id]])
  })

  it('keeps horizontal session tabs scrollable with stable tab widths', () => {
    const sessions = Array.from({ length: 12 }, (_, i) => fakeSession({ id: `session-${String(i).padStart(2, '0')}` }))
    const wrapper = mount(SessionTabs, {
      props: { sessions, activeId: sessions[0]?.id ?? null },
    })

    expect(wrapper.get('[data-testid="session-tabs-list"]').classes()).toContain('overflow-x-auto')
    expect(wrapper.findAll('li').every((li) => li.classes().includes('shrink-0'))).toBe(true)
  })
})
