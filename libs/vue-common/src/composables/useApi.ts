import type { ProblemDetail } from '../types'
import { ApiError } from '../types'

interface ApiOptions {
  baseUrl?: string
}

interface UseApiReturn {
  get: <T>(path: string) => Promise<T>
  post: <T>(path: string, body: unknown) => Promise<T>
  put: <T>(path: string, body: unknown) => Promise<T>
  del: (path: string) => Promise<void>
}

export function useApi(options: ApiOptions = {}): UseApiReturn {
  const baseUrl = options.baseUrl ?? '/api/v1'

  async function request<T>(path: string, init?: RequestInit): Promise<T> {
    const response = await fetch(`${baseUrl}${path}`, {
      ...init,
      headers: {
        'Content-Type': 'application/json',
        ...init?.headers,
      },
    })
    if (!response.ok) {
      throw await problemFromResponse(response)
    }
    return parseOkResponse<T>(response)
  }

  return {
    get: <T>(path: string) => request<T>(path),
    post: <T>(path: string, body: unknown) => request<T>(path, { method: 'POST', body: JSON.stringify(body) }),
    put: <T>(path: string, body: unknown) => request<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
    del: (path: string) => request<void>(path, { method: 'DELETE' }),
  }
}

/**
 * Parses a non-2xx response into an [ApiError] that wraps a
 * [ProblemDetail]. Falls back to a synthetic ProblemDetail when the
 * response body is missing or unparseable (e.g. an HTML 502 from the
 * ingress) so callers never have to special-case "no body" — the
 * structured shape is always available.
 */
export async function problemFromResponse(response: Response): Promise<ApiError> {
  let problem: ProblemDetail
  try {
    const parsed: ProblemDetail = await response.json()
    problem = parsed
  } catch {
    problem = {
      type: 'about:blank',
      title: response.statusText || 'Request failed',
      status: response.status,
    }
  }
  return new ApiError(problem)
}

/**
 * Deserializes a 2xx response body. Callers that POST to fire-and-forget
 * endpoints (e.g. `202 Accepted` with no body, the common shape for
 * eventually-consistent commands) used to hit `JSON.parse: unexpected
 * end of data` because `response.json()` is unforgiving on empty input.
 * The contract here:
 *
 * - `204 No Content` → `undefined` (the only status that's spec-bound to have no body).
 * - Empty body of any other 2xx → `undefined` (e.g. `202 Accepted`, idempotent `200`).
 * - Non-JSON Content-Type → `undefined` (a misconfigured proxy sending HTML on a 2xx, etc.).
 * - Otherwise parsed JSON as `T`.
 */
export async function parseOkResponse<T>(response: Response): Promise<T> {
  if (response.status === 204) {
    // eslint-disable-next-line ts/consistent-type-assertions -- 204 No Content has no body; caller expects T but receives undefined
    return undefined as unknown as T
  }
  const text = await response.text()
  if (text.length === 0) {
    // eslint-disable-next-line ts/consistent-type-assertions -- empty 2xx body has no payload to deserialize
    return undefined as unknown as T
  }
  const contentType = response.headers.get('Content-Type') ?? ''
  if (!contentType.toLowerCase().includes('json')) {
    // eslint-disable-next-line ts/consistent-type-assertions -- non-JSON 2xx body has no typed payload
    return undefined as unknown as T
  }
  return JSON.parse(text) as T
}
