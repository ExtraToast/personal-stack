import { ApiError } from '../types'
import { parseOkResponse, problemFromResponse } from './useApi'
import { useAuth } from './useAuth'

interface ApiWithAuthOptions {
  baseUrl?: string
  onUnauthorized?: () => void
}

interface ApiClient {
  get: <T>(path: string) => Promise<T>
  post: <T>(path: string, body: unknown) => Promise<T>
  put: <T>(path: string, body: unknown) => Promise<T>
  del: (path: string) => Promise<void>
}

export function useApiWithAuth(options: ApiWithAuthOptions = {}): ApiClient {
  const baseUrl = options.baseUrl ?? '/api/v1'
  const { logout, getCsrfToken } = useAuth()

  async function request<T>(path: string, init?: RequestInit): Promise<T> {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      ...Object.fromEntries(Object.entries(init?.headers ?? {})),
    }

    const method = init?.method ?? 'GET'
    if (method !== 'GET' && method !== 'HEAD') {
      const csrf = getCsrfToken()
      if (csrf) {
        headers['X-XSRF-TOKEN'] = csrf
      }
    }

    const response = await fetch(`${baseUrl}${path}`, { ...init, headers, credentials: 'include' })

    if (response.status === 401) {
      logout()
      options.onUnauthorized?.()
      throw new ApiError({
        type: 'about:blank',
        title: 'Unauthorized',
        status: 401,
        detail: 'Unauthorized',
      })
    }

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
