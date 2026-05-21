import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useApiWithAuth } from '../composables/useApiWithAuth'
import { ApiError } from '../types'

const fetchMock = vi.fn()

beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockReset()
  // useAuth() is pinia-backed; without a fresh store every test
  // shares stale state and the CSRF/logout indirection breaks.
  setActivePinia(createPinia())
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('useApiWithAuth', () => {
  it('parses a 200 JSON body', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ id: 'r-1' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    const api = useApiWithAuth({ baseUrl: '/api/v1' })

    const result = await api.get<{ id: string }>('/repositories/r-1')

    expect(result).toEqual({ id: 'r-1' })
  })

  it('returns undefined on a 202 Accepted with an empty body', async () => {
    // Regression for the production attach-key flow: assistant-api's
    // POST /repositories/{id}/key returns 202 Accepted with no body,
    // and useApiWithAuth used to crash with `JSON.parse: unexpected
    // end of data`, surfacing as "Could not attach the deploy key"
    // even though the command had been accepted.
    fetchMock.mockResolvedValueOnce(new Response('', { status: 202 }))
    const api = useApiWithAuth()

    const result = await api.post('/repositories/abc/key', {
      privateKeyOpenssh: 'x',
      publicKeyOpenssh: 'y',
    })

    expect(result).toBeUndefined()
  })

  it('returns undefined on a 204 No Content (DELETE)', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))
    const api = useApiWithAuth()

    const result = await api.del('/workspaces/123')

    expect(result).toBeUndefined()
  })

  it('throws an ApiError carrying the parsed ProblemDetail on a 4xx', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          type: 'https://jorisjonkers.dev/errors/kubernetes-api',
          title: 'Kubernetes API Error',
          status: 502,
          detail: 'cannot patch resource',
          kubernetesCode: 403,
          kubernetesReason: 'Forbidden',
        }),
        { status: 502, headers: { 'Content-Type': 'application/problem+json' } },
      ),
    )
    const api = useApiWithAuth()

    const promise = api.post('/workspaces', { name: 'w' })

    await expect(promise).rejects.toBeInstanceOf(ApiError)
    await expect(promise).rejects.toMatchObject({
      status: 502,
      problem: { kubernetesCode: 403, kubernetesReason: 'Forbidden' },
    })
  })

  it('attaches X-XSRF-TOKEN to non-GET requests when a csrf cookie is present', async () => {
    document.cookie = 'XSRF-TOKEN=tok-abc; path=/'
    fetchMock.mockResolvedValueOnce(new Response('', { status: 202 }))
    const api = useApiWithAuth()

    await api.post('/repositories/abc/key', {})

    const [, init] = fetchMock.mock.calls[0]!
    expect(init.method).toBe('POST')
    expect(init.headers).toMatchObject({ 'X-XSRF-TOKEN': 'tok-abc' })
    // cleanup
    document.cookie = 'XSRF-TOKEN=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT'
  })
})
