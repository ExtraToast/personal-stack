import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { useSessionLabelsStore } from '../stores/sessionLabels'

describe('useSessionLabelsStore', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('returns null for an unlabelled session', () => {
    const store = useSessionLabelsStore()
    expect(store.labelFor('sess-1')).toBeNull()
  })

  it('rename sets a label and persists it to localStorage', () => {
    const store = useSessionLabelsStore()
    store.rename('sess-1', 'backend work')
    expect(store.labelFor('sess-1')).toBe('backend work')
    expect(JSON.parse(localStorage.getItem('assistant-ui:session-labels')!)).toEqual({
      'sess-1': 'backend work',
    })
  })

  it('trims whitespace and clears the label when renamed to blank', () => {
    const store = useSessionLabelsStore()
    store.rename('sess-1', '  spaced  ')
    expect(store.labelFor('sess-1')).toBe('spaced')
    store.rename('sess-1', '   ')
    expect(store.labelFor('sess-1')).toBeNull()
    expect(localStorage.getItem('assistant-ui:session-labels')).toBe('{}')
  })

  it('hydrates existing labels from localStorage on init', () => {
    localStorage.setItem('assistant-ui:session-labels', JSON.stringify({ 'sess-9': 'kept' }))
    setActivePinia(createPinia())
    const store = useSessionLabelsStore()
    expect(store.labelFor('sess-9')).toBe('kept')
  })

  it('survives malformed localStorage by starting empty', () => {
    localStorage.setItem('assistant-ui:session-labels', 'not json')
    setActivePinia(createPinia())
    const store = useSessionLabelsStore()
    expect(store.labelFor('sess-1')).toBeNull()
  })
})
