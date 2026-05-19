import { describe, expect, it, vi } from 'vitest'
import { useMutationState } from '../composables/useMutationState'

function deferred<T>(): { promise: Promise<T>; resolve: (v: T) => void; reject: (e: unknown) => void } {
  let resolve!: (v: T) => void
  let reject!: (e: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

describe('useMutationState', () => {
  it('starts in idle status with no error', () => {
    const m = useMutationState()
    expect(m.status.value).toBe('idle')
    expect(m.pending.value).toBe(false)
    expect(m.success.value).toBe(false)
    expect(m.failure.value).toBe(false)
    expect(m.error.value).toBeNull()
  })

  it('transitions idle → pending → success on resolve', async () => {
    vi.useFakeTimers()
    const m = useMutationState({ resetDelayMs: 1_000 })
    const d = deferred<string>()
    const promise = m.run(() => d.promise)
    expect(m.status.value).toBe('pending')
    expect(m.pending.value).toBe(true)

    d.resolve('done')
    await expect(promise).resolves.toBe('done')
    expect(m.status.value).toBe('success')

    vi.advanceTimersByTime(1_000)
    expect(m.status.value).toBe('idle')
    vi.useRealTimers()
  })

  it('transitions idle → pending → failure on reject and re-throws', async () => {
    vi.useFakeTimers()
    const m = useMutationState({ resetDelayMs: 1_000 })
    const boom = new Error('nope')
    await expect(m.run(() => Promise.reject(boom))).rejects.toBe(boom)
    expect(m.status.value).toBe('failure')
    expect(m.error.value).toBe(boom)

    vi.advanceTimersByTime(1_000)
    expect(m.status.value).toBe('idle')
    expect(m.error.value).toBeNull()
    vi.useRealTimers()
  })

  it('returns null when re-entered while pending', async () => {
    const m = useMutationState()
    const d = deferred<string>()
    const first = m.run(() => d.promise)
    const second = await m.run(() => Promise.resolve('overlap'))
    expect(second).toBeNull()
    d.resolve('first')
    await expect(first).resolves.toBe('first')
  })

  it('reset() drops back to idle synchronously', async () => {
    const m = useMutationState({ resetDelayMs: 10_000 })
    await m.run(() => Promise.resolve('ok'))
    expect(m.status.value).toBe('success')
    m.reset()
    expect(m.status.value).toBe('idle')
    expect(m.error.value).toBeNull()
  })

  it('resetDelayMs=0 leaves the indicator visible until manual reset', async () => {
    vi.useFakeTimers()
    const m = useMutationState({ resetDelayMs: 0 })
    await m.run(() => Promise.resolve('ok'))
    expect(m.status.value).toBe('success')
    vi.advanceTimersByTime(60_000)
    expect(m.status.value).toBe('success')
    vi.useRealTimers()
  })
})
