import { beforeEach, describe, expect, it, vi } from 'vitest'
import { _resetToastStateForTests, useToast } from '../composables/useToast'

beforeEach(() => _resetToastStateForTests())

describe('useToast', () => {
  it('queues a toast with sensible defaults', () => {
    const toast = useToast()
    const t = toast.push({ title: 'Saved' })
    expect(t.id).toBe(1)
    expect(t.kind).toBe('info')
    expect(t.durationMs).toBe(5_000)
    expect(toast.toasts.length).toBe(1)
  })

  it('dismisses by id', () => {
    const toast = useToast()
    const a = toast.push({ title: 'A', durationMs: 0 })
    const b = toast.push({ title: 'B', durationMs: 0 })
    expect(toast.toasts.length).toBe(2)
    toast.dismiss(a.id)
    expect(toast.toasts.length).toBe(1)
    expect(toast.toasts[0]?.id).toBe(b.id)
  })

  it('auto-dismisses after durationMs', () => {
    vi.useFakeTimers()
    const toast = useToast()
    toast.success('Done')
    expect(toast.toasts.length).toBe(1)
    vi.advanceTimersByTime(5_000)
    expect(toast.toasts.length).toBe(0)
    vi.useRealTimers()
  })

  it('sticks when durationMs=0', () => {
    vi.useFakeTimers()
    const toast = useToast()
    toast.push({ title: 'Sticky', durationMs: 0 })
    vi.advanceTimersByTime(60_000)
    expect(toast.toasts.length).toBe(1)
    vi.useRealTimers()
  })

  it('clear() empties the queue', () => {
    const toast = useToast()
    toast.info('one')
    toast.info('two')
    toast.clear()
    expect(toast.toasts.length).toBe(0)
  })

  it('success/error/info helpers tag the kind correctly', () => {
    const toast = useToast()
    expect(toast.success('ok').kind).toBe('success')
    expect(toast.error('boom').kind).toBe('error')
    expect(toast.info('fyi').kind).toBe('info')
  })
})
