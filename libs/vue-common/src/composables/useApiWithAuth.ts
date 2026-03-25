import type { ProblemDetail } from '../types'
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
  const { getAccessToken, logout } = useAuth()

  async function request<T>(path: string, init?: RequestInit): Promise<T> {
    const token = getAccessToken()
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      ...(init?.headers as Record<string, string> | undefined),
    }

    if (token) {
      headers.Authorization = `Bearer ${token}`
    }

    const response = await fetch(`${baseUrl}${path}`, { ...init, headers })

    if (response.status === 401) {
      logout()
      options.onUnauthorized?.()
      throw new Error('Unauthorized')
    }

    if (!response.ok) {
      const problem: ProblemDetail = await response.json()
      throw problem
    }

    if (response.status === 204) {
      return undefined as unknown as T
    }

    return response.json() as Promise<T>
  }

  return {
    get: <T>(path: string) => request<T>(path),
    post: <T>(path: string, body: unknown) =>
      request<T>(path, { method: 'POST', body: JSON.stringify(body) }),
    put: <T>(path: string, body: unknown) =>
      request<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
    del: (path: string) => request<void>(path, { method: 'DELETE' }),
  }
}
