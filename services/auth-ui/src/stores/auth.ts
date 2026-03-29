import type { ProblemDetail } from '@personal-stack/vue-common'
import { useAuth } from '@personal-stack/vue-common'
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { register as apiRegister, sessionLogin } from '@/features/auth/services/authService'

export const useAuthStore = defineStore('auth', () => {
  const { user, isAuthenticated, setUser, fetchUser, logout: authLogout } = useAuth()

  const isLoading = ref(false)
  const error = ref<string | null>(null)
  const totpRequired = ref(false)

  async function login(username: string, password: string, totpCode?: string): Promise<void> {
    isLoading.value = true
    error.value = null
    totpRequired.value = false
    try {
      const response = await sessionLogin(username, password, totpCode)

      if (response.totpRequired) {
        totpRequired.value = true
        return
      }

      if (response.success && response.user) {
        const { id, username, role: rawRole } = response.user
        const role = (['ADMIN', 'USER', 'READONLY'] as const).find((r) => r === rawRole) ?? 'USER'
        setUser({ id, username, email: '', role })
      }
    } catch (e: unknown) {
      const msg = isProblemDetail(e) ? (e.detail ?? e.title) : 'Login failed'
      error.value = msg
      throw e
    } finally {
      isLoading.value = false
    }
  }

  async function register(username: string, email: string, password: string): Promise<void> {
    isLoading.value = true
    error.value = null
    try {
      await apiRegister({ username, email, password })
    } catch (e: unknown) {
      const msg = isProblemDetail(e) ? (e.detail ?? e.title) : 'Registration failed'
      error.value = msg
      throw e
    } finally {
      isLoading.value = false
    }
  }

  async function checkSession(): Promise<void> {
    await fetchUser()
  }

  function logout(): void {
    authLogout()
    totpRequired.value = false
  }

  return {
    user,
    isAuthenticated,
    isLoading,
    error,
    totpRequired,
    login,
    register,
    logout,
    checkSession,
  }
})

function isProblemDetail(e: unknown): e is ProblemDetail {
  return typeof e === 'object' && e !== null && 'title' in e && 'status' in e
}
