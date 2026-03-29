import type { User } from '../types'
import { computed, ref } from 'vue'

// Module-level state — shared across all useAuth() calls (singleton pattern)
const user = ref<User | null>(null)

function getCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`))
  return match ? decodeURIComponent(match[1]) : null
}

// eslint-disable-next-line ts/explicit-function-return-type -- composable return type is complex and inferred
export function useAuth() {
  const isAuthenticated = computed(() => user.value !== null)

  function setUser(u: User | null): void {
    user.value = u
  }

  async function fetchUser(baseUrl = '/api/v1'): Promise<User | null> {
    try {
      const response = await fetch(`${baseUrl}/auth/me`, { credentials: 'include' })
      if (!response.ok) {
        user.value = null
        return null
      }
      const data: { id: string; username: string; role: string } = await response.json()
      const validRoles = ['ADMIN', 'USER', 'READONLY'] as const
      const matched = validRoles.find((r) => r === data.role)
      const u: User = { id: data.id, username: data.username, email: '', role: matched ?? 'USER' }
      user.value = u
      return u
    } catch {
      user.value = null
      return null
    }
  }

  function logout(): void {
    user.value = null
  }

  function getCsrfToken(): string | null {
    return getCookie('XSRF-TOKEN')
  }

  return {
    user,
    isAuthenticated,
    setUser,
    fetchUser,
    logout,
    getCsrfToken,
  }
}
