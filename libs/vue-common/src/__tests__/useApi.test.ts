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

  it('returns undefined on a 204 No Content', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))
    const api = useApi()
    const result = await api.del('/workspaces/123')
    expect(result).toBeUndefined()
  })

  it('returns undefined on a 202 Accepted with an empty body (fire-and-forget commands)', async () => {
    // Regression: assistant-api's POST /repositories/{id}/key returns
    // 202 Accepted with no body. The previous parser called
    // response.json() unconditionally on every 2xx that wasn't 204 and
    // crashed with `JSON.parse: unexpected end of data`, surfacing as
    // a misleading "Could not attach the deploy key" toast in the UI
    // even when the backend had accepted the command.
    fetchMock.mockResolvedValueOnce(new Response('', { status: 202 }))
    const api = useApi()
    const result = await api.post('/repositories/abc/key', { privateKeyOpenssh: 'x', publicKeyOpenssh: 'y' })
    expect(result).toBeUndefined()
  })

  it('returns undefined on a 200 with an empty body', async () => {
    fetchMock.mockResolvedValueOnce(new Response('', { status: 200 }))
    const api = useApi()
    const result = await api.get('/anything')
    expect(result).toBeUndefined()
  })

  it('returns undefined on a 2xx with a non-JSON Content-Type', async () => {
    // An upstream proxy that surfaces a plain-text health blurb on a
    // 200 must not crash the client. The endpoint is non-JSON; the
    // caller's `T` is therefore irrelevant, and `undefined` is the
    // honest default.
    fetchMock.mockResolvedValueOnce(new Response('OK', { status: 200, headers: { 'Content-Type': 'text/plain' } }))
    const api = useApi()
    const result = await api.get('/healthz')
    expect(result).toBeUndefined()
  })
})
