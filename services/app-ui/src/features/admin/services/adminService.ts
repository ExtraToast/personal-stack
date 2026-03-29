import { SERVICE_REGISTRY } from '@/features/apps'

const AUTH_BASE_URL: string = import.meta.env.VITE_AUTH_URL ?? 'https://auth.jorisjonkers.dev'
const ADMIN_BASE = `${AUTH_BASE_URL}/api/v1/admin`

export interface AdminUser {
  id: string
  username: string
  email: string
  role: string
  emailConfirmed: boolean
  totpEnabled: boolean
  servicePermissions: string[]
  createdAt: string
}

function getCsrfToken(): string | null {
  if (typeof document === 'undefined') return null
  const cookie = document.cookie.split('; ').find((entry) => entry.startsWith('XSRF-TOKEN='))

  return cookie ? decodeURIComponent(cookie.slice('XSRF-TOKEN='.length)) : null
}

async function authedFetch(url: string, init?: RequestInit): Promise<Response> {
  const method = init?.method?.toUpperCase() ?? 'GET'
  const headers = new Headers(init?.headers)
  headers.set('Content-Type', 'application/json')
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const csrf = getCsrfToken()
    if (csrf) {
      headers.set('X-XSRF-TOKEN', csrf)
    }
  }
  const response = await fetch(url, { ...init, headers, credentials: 'include' })
  if (!response.ok) throw new Error(`Request failed: ${response.status}`)
  return response
}

export async function fetchUsers(): Promise<AdminUser[]> {
  const res = await authedFetch(`${ADMIN_BASE}/users`)
  const users: AdminUser[] = await res.json()
  return users
}

export async function fetchUser(id: string): Promise<AdminUser> {
  const res = await authedFetch(`${ADMIN_BASE}/users/${id}`)
  const user: AdminUser = await res.json()
  return user
}

export async function updateUserRole(id: string, role: string): Promise<AdminUser> {
  const res = await authedFetch(`${ADMIN_BASE}/users/${id}/role`, {
    method: 'PATCH',
    body: JSON.stringify({ role }),
  })
  const user: AdminUser = await res.json()
  return user
}

export async function updateUserServices(id: string, services: string[]): Promise<AdminUser> {
  const res = await authedFetch(`${ADMIN_BASE}/users/${id}/services`, {
    method: 'PUT',
    body: JSON.stringify({ services }),
  })
  const user: AdminUser = await res.json()
  return user
}

export async function deleteUser(id: string): Promise<void> {
  await authedFetch(`${ADMIN_BASE}/users/${id}`, { method: 'DELETE' })
}

export const ALL_ROLES = ['ADMIN', 'USER', 'READONLY'] as const
export const ALL_SERVICES = SERVICE_REGISTRY.map((s) => s.permission)
