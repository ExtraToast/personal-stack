import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useApi } from '../composables/useApi'
import { ApiError } from '../types'

const fetchMock = vi.fn()

beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockReset()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

function jsonResponse(body: unknown, init: ResponseInit = {}): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
    ...init,
  })
}

describe('useApi', () => {
  it('returns parsed JSON on a 2xx', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ id: 'r-1', name: 'foo' }))
    const api = useApi({ baseUrl: '/api/v1' })

    const result = await api.get<{ id: string }>('/repositories/r-1')

    expect(result).toEqual({ id: 'r-1', name: 'foo' })
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/repositories/r-1', expect.any(Object))
  })

  it('throws an ApiError carrying the parsed ProblemDetail on a non-2xx', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(
        {
          type: 'https://jorisjonkers.dev/errors/kubernetes-api',
          title: 'Kubernetes API Error',
          status: 502,
          detail: 'Kubernetes API request failed (code=422, reason=Invalid): bad spec',
          kubernetesCode: 422,
          kubernetesReason: 'Invalid',
          traceId: 'abc-123',
          errors: [{ field: 'spec.image', message: 'must be set' }],
        },
        { status: 502, statusText: 'Bad Gateway' },
      ),
    )
    const api = useApi()

    const promise = api.post('/sessions', { foo: 'bar' })

    await expect(promise).rejects.toBeInstanceOf(ApiError)
    await expect(promise).rejects.toMatchObject({
      status: 502,
      message: 'Kubernetes API request failed (code=422, reason=Invalid): bad spec',
      problem: {
        kubernetesCode: 422,
        kubernetesReason: 'Invalid',
        traceId: 'abc-123',
        errors: [{ field: 'spec.image', message: 'must be set' }],
      },
    })
  })

  it('falls back to a synthetic ProblemDetail when the error body is unparseable', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response('<html>502 Bad Gateway</html>', {
        status: 502,
        statusText: 'Bad Gateway',
        headers: { 'Content-Type': 'text/html' },
      }),
    )
    const api = useApi()

    try {
      await api.get('/anything')
      expect.fail('expected ApiError')
    } catch (e) {
      expect(e).toBeInstanceOf(ApiError)
      if (!(e instanceof ApiError)) throw e
      expect(e.status).toBe(502)
      expect(e.problem.title).toBe('Bad Gateway')
      expect(e.message).toBe('Bad Gateway')
    }
  })
})
