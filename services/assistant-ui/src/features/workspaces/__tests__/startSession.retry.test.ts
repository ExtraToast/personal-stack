import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/lib/vueWebCommons'
import { startSession } from '../services/workspaceService'

const post = vi.fn()
vi.mock('@/lib/vueWebCommons', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/vueWebCommons')>()
  return { ...actual, useApiWithAuth: () => ({ post, get: vi.fn(), del: vi.fn() }) }
})

function err(status: number, retryAfterSeconds?: number): ApiError {
  return new ApiError({
    type: 'about:blank',
    title: 'x',
    status,
    ...(retryAfterSeconds === undefined ? {} : { retryAfterSeconds }),
  })
}

describe('startSession cold-start retry', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    post.mockReset()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('retries through 503s and resolves once the runner is ready', async () => {
    post
      .mockRejectedValueOnce(err(503, 1))
      .mockRejectedValueOnce(err(503, 1))
      .mockResolvedValueOnce({ sessionId: 'sess-1' })
    const waiting = vi.fn()

    const promise = startSession('ws-1', 'CLAUDE', waiting)
    await vi.advanceTimersByTimeAsync(1000)
    await vi.advanceTimersByTimeAsync(1000)

    await expect(promise).resolves.toEqual({ sessionId: 'sess-1' })
    expect(post).toHaveBeenCalledTimes(3)
    expect(waiting).toHaveBeenCalledTimes(2)
  })

  it('waits the server-supplied retryAfterSeconds between attempts', async () => {
    post.mockRejectedValueOnce(err(503, 7)).mockResolvedValueOnce({ sessionId: 'sess-1' })
    const waiting = vi.fn()

    const promise = startSession('ws-1', 'CLAUDE', waiting)
    await vi.advanceTimersByTimeAsync(7000)

    await expect(promise).resolves.toEqual({ sessionId: 'sess-1' })
    expect(waiting).toHaveBeenCalledWith(7)
  })

  it('does not retry a non-503 error', async () => {
    post.mockRejectedValueOnce(err(500))
    await expect(startSession('ws-1', 'CLAUDE')).rejects.toBeInstanceOf(ApiError)
    expect(post).toHaveBeenCalledTimes(1)
  })
})
