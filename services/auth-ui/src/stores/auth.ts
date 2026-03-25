import type { ProblemDetail } from '@private-stack/vue-common'
import { useAuth } from '@private-stack/vue-common'
import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  login as apiLogin,
  refresh as apiRefresh,
  register as apiRegister,
} from '@/features/auth/services/authService'

export const useAuthStore = defineStore('auth', () => {
  const {
    user,
    isAuthenticated,
    setTokens,
    getAccessToken,
    getRefreshToken,
    logout: authLogout,
  } = useAuth()

  const isLoading = ref(false)
  const error = ref<string | null>(null)

  async function login(username: string, password: string): Promise<void> {
    isLoading.value = true
    error.value = null
    try {
      const tokens = await apiLogin({ username, password })
      setTokens(tokens)
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

  async function refreshTokens(): Promise<boolean> {
    const refreshToken = getRefreshToken()
    if (!refreshToken) return false
    try {
      const tokens = await apiRefresh(refreshToken)
      setTokens(tokens)
      return true
    } catch {
      authLogout()
      return false
    }
  }

  function logout(): void {
    authLogout()
  }

  return {
    user,
    isAuthenticated,
    isLoading,
    error,
    login,
    register,
    logout,
    refreshTokens,
    getAccessToken,
  }
})

function isProblemDetail(e: unknown): e is ProblemDetail {
  return typeof e === 'object' && e !== null && 'title' in e && 'status' in e
}
