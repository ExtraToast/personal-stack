import { ApiError } from '@personal-stack/vue-common'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { startSession } from '../services/workspaceService'

const post = vi.fn()
vi.mock('@personal-stack/vue-common', async (orig) => {
  const actual = await orig<typeof import('@personal-stack/vue-common')>()
  return { ...actual, useApiWithAuth: () => ({ post, get: vi.fn(), del: vi.fn(), put: vi.fn() }) }
})

function notReady(retryAfterSeconds = 5): ApiError {
  return new ApiError({
    type: 'https://jorisjonkers.dev/errors/agent-runner-unavailable',
    title: 'Agent runner not ready',
    status: 503,
    detail: 'runner is NotReady',
    retryAfterSeconds,
  })
}

describe('startSession runner cold-start retry', () => {
  afterEach(() => {
    vi.useRealTimers()
    post.mockReset()
  })

  it('retries on 503 NotReady until the runner is ready', async () => {
    vi.useFakeTimers()
    post
      .mockRejectedValueOnce(notReady(5))
      .mockRejectedValueOnce(notReady(5))
      .mockResolvedValueOnce({ sessionId: 's1' })
    const onWaiting = vi.fn()

    const p = startSession('w1', 'CLAUDE', { onWaiting })
    await vi.advanceTimersByTimeAsync(5000)
    await vi.advanceTimersByTimeAsync(5000)
    await expect(p).resolves.toEqual({ sessionId: 's1' })
    expect(post).toHaveBeenCalledTimes(3)
    expect(onWaiting).toHaveBeenCalledTimes(2)
  })

  it('gives up past the wait budget rather than retrying forever', async () => {
    vi.useFakeTimers()
    post.mockRejectedValue(notReady(5))
    const p = startSession('w1', 'CLAUDE', { maxWaitMs: 6000 })
    const assertion = expect(p).rejects.toBeInstanceOf(ApiError)
    await vi.advanceTimersByTimeAsync(5000)
    await assertion
  })

  it('does not retry a non-503 error', async () => {
    post.mockRejectedValueOnce(new ApiError({ type: 'about:blank', title: 'Bad Request', status: 400, detail: 'nope' }))
    await expect(startSession('w1', 'CLAUDE')).rejects.toMatchObject({ status: 400 })
    expect(post).toHaveBeenCalledTimes(1)
  })
})
