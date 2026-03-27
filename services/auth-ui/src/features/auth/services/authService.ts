import type { AuthTokens } from '@personal-stack/vue-common'
import { useAuth } from '@personal-stack/vue-common'

interface LoginCredentials {
  username: string
  password: string
}

interface RegisterData {
  username: string
  email: string
  password: string
}

const API_BASE = '/api/v1'

/* eslint-disable ts/consistent-type-assertions -- generic response handling requires casts */
async function post<T>(path: string, body: unknown, headers: Record<string, string> = {}): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    throw await response.json()
  }
  if (response.status === 204) {
    return undefined as unknown as T
  }
  return response.json() as Promise<T>
}

function authPost<T>(path: string, body: unknown): Promise<T> {
  const { getAccessToken } = useAuth()
  const token = getAccessToken()
  if (!token) {
    return Promise.reject(new Error('Not authenticated'))
  }
  return post<T>(path, body, { Authorization: `Bearer ${token}` })
}
/* eslint-enable ts/consistent-type-assertions */

export async function login(credentials: LoginCredentials): Promise<AuthTokens> {
  return post<AuthTokens>('/auth/login', credentials)
}

export async function refresh(refreshToken: string): Promise<AuthTokens> {
  return post<AuthTokens>('/auth/refresh', { refreshToken })
}

export async function register(data: RegisterData): Promise<void> {
  return post<void>('/users/register', data)
}

export async function enrollTotp(): Promise<{
  secret: string
  qrUri: string
}> {
  return authPost<{ secret: string; qrUri: string }>('/totp/enroll', {})
}

export async function verifyTotp(code: string): Promise<void> {
  return authPost<void>('/totp/verify', { code })
}
