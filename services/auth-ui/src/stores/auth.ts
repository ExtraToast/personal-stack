import type { ProblemDetail } from '@personal-stack/vue-common'
import { useAuth } from '@personal-stack/vue-common'
import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  login as apiLogin,
  refresh as apiRefresh,
  register as apiRegister,
  submitTotpChallenge as apiTotpChallenge,
} from '@/features/auth/services/authService'

export const useAuthStore = defineStore('auth', () => {
  const { user, isAuthenticated, setTokens, getAccessToken, getRefreshToken, logout: authLogout } = useAuth()

  const isLoading = ref(false)
  const error = ref<string | null>(null)
  const totpRequired = ref(false)
  const totpChallengeToken = ref<string | null>(null)

  async function login(username: string, password: string): Promise<void> {
    isLoading.value = true
    error.value = null
    totpRequired.value = false
    totpChallengeToken.value = null
    try {
      const response = await apiLogin({ username, password })

      if (response.totpRequired && response.totpChallengeToken) {
        totpRequired.value = true
        totpChallengeToken.value = response.totpChallengeToken
        return
      }

      if (response.accessToken && response.refreshToken) {
        setTokens({
          accessToken: response.accessToken,
          refreshToken: response.refreshToken,
          expiresIn: response.expiresIn ?? 900,
        })
      }
    } catch (e: unknown) {
      const msg = isProblemDetail(e) ? (e.detail ?? e.title) : 'Login failed'
      error.value = msg
      throw e
    } finally {
      isLoading.value = false
    }
  }

  async function verifyTotpChallenge(code: string): Promise<void> {
    if (!totpChallengeToken.value) {
      error.value = 'No TOTP challenge in progress'
      throw new Error('No TOTP challenge token')
    }
    isLoading.value = true
    error.value = null
    try {
      const response = await apiTotpChallenge(totpChallengeToken.value, code)
      if (response.accessToken && response.refreshToken) {
        setTokens({
          accessToken: response.accessToken,
          refreshToken: response.refreshToken,
          expiresIn: response.expiresIn ?? 900,
        })
      }
      totpRequired.value = false
      totpChallengeToken.value = null
    } catch (e: unknown) {
      const msg = isProblemDetail(e) ? (e.detail ?? e.title) : 'Invalid code'
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
    totpRequired.value = false
    totpChallengeToken.value = null
  }

  return {
    user,
    isAuthenticated,
    isLoading,
    error,
    totpRequired,
    login,
    verifyTotpChallenge,
    register,
    logout,
    refreshTokens,
    getAccessToken,
  }
})

function isProblemDetail(e: unknown): e is ProblemDetail {
  return typeof e === 'object' && e !== null && 'title' in e && 'status' in e
}
