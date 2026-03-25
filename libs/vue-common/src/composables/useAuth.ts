import { ref, computed } from 'vue'
import type { Ref } from 'vue'
import type { User } from '../types'

interface UseAuthReturn {
  user: Ref<User | null>
  isAuthenticated: Ref<boolean>
  login: (token: string) => void
  logout: () => void
}

export function useAuth(): UseAuthReturn {
  const user = ref<User | null>(null)
  const isAuthenticated = computed(() => user.value !== null)

  function login(_token: string): void {
    // TODO: decode JWT and set user
  }

  function logout(): void {
    user.value = null
  }

  return { user, isAuthenticated, login, logout }
}
