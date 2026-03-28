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

async function authedFetch(token: string, url: string, init?: RequestInit): Promise<Response> {
  const response = await fetch(url, {
    ...init,
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      ...init?.headers,
    },
  })
  if (!response.ok) throw new Error(`Request failed: ${response.status}`)
  return response
}

export async function fetchUsers(token: string): Promise<AdminUser[]> {
  const res = await authedFetch(token, `${ADMIN_BASE}/users`)
  const users: AdminUser[] = await res.json()
  return users
}

export async function fetchUser(token: string, id: string): Promise<AdminUser> {
  const res = await authedFetch(token, `${ADMIN_BASE}/users/${id}`)
  const user: AdminUser = await res.json()
  return user
}

export async function updateUserRole(token: string, id: string, role: string): Promise<AdminUser> {
  const res = await authedFetch(token, `${ADMIN_BASE}/users/${id}/role`, {
    method: 'PATCH',
    body: JSON.stringify({ role }),
  })
  const user: AdminUser = await res.json()
  return user
}

export async function updateUserServices(token: string, id: string, services: string[]): Promise<AdminUser> {
  const res = await authedFetch(token, `${ADMIN_BASE}/users/${id}/services`, {
    method: 'PUT',
    body: JSON.stringify({ services }),
  })
  const user: AdminUser = await res.json()
  return user
}

export async function deleteUser(token: string, id: string): Promise<void> {
  await authedFetch(token, `${ADMIN_BASE}/users/${id}`, { method: 'DELETE' })
}

export const ALL_ROLES = ['ADMIN', 'USER', 'READONLY'] as const
export const ALL_SERVICES = SERVICE_REGISTRY.map((s) => s.permission)
