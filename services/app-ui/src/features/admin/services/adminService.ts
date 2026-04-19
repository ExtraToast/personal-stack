import { SERVICE_REGISTRY } from '@/features/apps'
import { AUTH_BASE_URL, authedFetch } from '@/lib/authedFetch'

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
