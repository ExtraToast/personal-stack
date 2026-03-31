import { useAuth } from '@personal-stack/vue-common'

interface RegisterData {
  username: string
  email: string
  firstName: string
  lastName: string
  password: string
}

const API_BASE = '/api/v1'

function getCsrfToken(): string | null {
  const { getCsrfToken: csrf } = useAuth()
  return csrf()
}

/* eslint-disable ts/consistent-type-assertions -- generic response handling requires casts */
async function post<T>(path: string, body: unknown, extraHeaders: Record<string, string> = {}): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json', ...extraHeaders }
  const csrf = getCsrfToken()
  if (csrf) {
    headers['X-XSRF-TOKEN'] = csrf
  }
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers,
    credentials: 'include',
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
/* eslint-enable ts/consistent-type-assertions */

export async function register(data: RegisterData): Promise<void> {
  return post<void>('/users/register', data)
}

export async function enrollTotp(): Promise<{
  secret: string
  qrUri: string
}> {
  return post<{ secret: string; qrUri: string }>('/totp/enroll', {})
}

export async function verifyTotp(code: string): Promise<void> {
  return post<void>('/totp/verify', { code })
}

export async function confirmEmail(token: string): Promise<{ message: string }> {
  const response = await fetch(`${API_BASE}/auth/confirm-email?token=${encodeURIComponent(token)}`, {
    credentials: 'include',
  })
  if (!response.ok) throw await response.json()
  const json: { message: string } = await response.json()
  return json
}

export async function resendConfirmation(email: string): Promise<{ message: string }> {
  return post<{ message: string }>('/auth/resend-confirmation', { email })
}

export interface SessionLoginResponse {
  success: boolean
  totpRequired: boolean
  user?: {
    id: string
    username: string
    role: string
  }
}

/**
 * Authenticates the user and creates a server-side session (SESSION cookie).
 * The session cookie is used for all subsequent authenticated requests.
 */
export async function sessionLogin(
  username: string,
  password: string,
  totpCode?: string,
): Promise<SessionLoginResponse> {
  const response = await fetch(`${API_BASE}/auth/session-login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ username, password, ...(totpCode ? { totpCode } : {}) }),
  })
  if (!response.ok) {
    throw await response.json()
  }
  const json: SessionLoginResponse = await response.json()
  return json
}
