import type { AuthUser } from '../types'

const AUTH_BASE_URL: string = import.meta.env.VITE_AUTH_URL ?? 'https://auth.jorisjonkers.dev'

interface SessionPayload {
  id?: string
  sub?: string
  username: string
  email?: string
  role?: string
  roles?: string[]
}

export function startLoginFlow(): void {
  const redirect = encodeURIComponent(window.location.href)
  window.location.href = `${AUTH_BASE_URL}/login?redirect=${redirect}`
}

export async function checkSession(): Promise<AuthUser> {
  const response = await fetch(`${AUTH_BASE_URL}/api/v1/auth/me`, {
    credentials: 'include',
  })

  if (!response.ok) throw new Error(`Session check failed: ${response.status}`)
  const payload: SessionPayload = await response.json()
  return {
    sub: payload.sub ?? payload.id ?? '',
    username: payload.username,
    email: payload.email ?? '',
    roles: payload.roles ?? (payload.role ? [`ROLE_${payload.role}`] : []),
  }
}

export function buildLogoutUrl(): string {
  return `${AUTH_BASE_URL}/api/v1/auth/logout`
}
