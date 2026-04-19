import { AUTH_BASE_URL, authedFetch } from '@/lib/authedFetch'

const AUTH_API = `${AUTH_BASE_URL}/api/v1`

export interface ProfileData {
  id: string
  username: string
  email: string
  firstName: string
  lastName: string
  role: string
  totpEnabled: boolean
  createdAt: string
}

export async function fetchProfile(): Promise<ProfileData> {
  const res = await authedFetch(`${AUTH_API}/users/me`)
  const data: ProfileData = await res.json()
  return data
}

export async function updateProfile(data: { firstName: string; lastName: string }): Promise<ProfileData> {
  const res = await authedFetch(`${AUTH_API}/users/me`, {
    method: 'PATCH',
    body: JSON.stringify(data),
  })
  const result: ProfileData = await res.json()
  return result
}

export async function changePassword(data: { currentPassword: string; newPassword: string }): Promise<void> {
  await authedFetch(`${AUTH_API}/auth/change-password`, {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export async function forgotPassword(email: string): Promise<void> {
  await authedFetch(`${AUTH_API}/auth/forgot-password`, {
    method: 'POST',
    body: JSON.stringify({ email }),
  })
}

export async function resetPassword(token: string, newPassword: string): Promise<void> {
  await authedFetch(`${AUTH_API}/auth/reset-password`, {
    method: 'POST',
    body: JSON.stringify({ token, newPassword }),
  })
}
