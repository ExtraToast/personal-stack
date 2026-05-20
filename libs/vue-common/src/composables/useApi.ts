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
    const json: Promise<T> = response.json()
    return json
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
