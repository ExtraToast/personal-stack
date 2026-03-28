import type { AuthUser, JwtPayload } from '../types'
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { buildLogoutUrl, exchangeCodeForToken, refreshAccessToken, startLoginFlow } from '../services/authService'

const ACCESS_TOKEN_KEY = 'access_token'
const REFRESH_TOKEN_KEY = 'refresh_token'

function parseJwtPayload(token: string): JwtPayload | null {
  try {
    const parts = token.split('.')
    if (parts.length !== 3 || !parts[1]) return null
    const payload: JwtPayload = JSON.parse(atob(parts[1].replace(/-/gu, '+').replace(/_/gu, '/')))
    return payload
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(localStorage.getItem(ACCESS_TOKEN_KEY))
  const refreshToken = ref<string | null>(localStorage.getItem(REFRESH_TOKEN_KEY))
  const user = ref<AuthUser | null>(null)
  const roles = ref<string[]>([])

  const isAuthenticated = computed(() => accessToken.value !== null)
  const isAdmin = computed(() => roles.value.includes('ROLE_ADMIN'))
  const servicePermissions = computed(() =>
    roles.value.filter((r) => r.startsWith('SERVICE_')).map((r) => r.replace('SERVICE_', '')),
  )

  function applyToken(token: string): void {
    const payload = parseJwtPayload(token)
    if (!payload) return
    roles.value = payload.roles ?? []
    user.value = {
      sub: payload.sub,
      username: payload.username ?? payload.sub,
      email: payload.email ?? '',
    }
  }

  if (accessToken.value) {
    applyToken(accessToken.value)
  }

  function storeTokens(access: string, refresh: string): void {
    accessToken.value = access
    refreshToken.value = refresh
    localStorage.setItem(ACCESS_TOKEN_KEY, access)
    localStorage.setItem(REFRESH_TOKEN_KEY, refresh)
    applyToken(access)
  }

  function clearTokens(): void {
    accessToken.value = null
    refreshToken.value = null
    user.value = null
    roles.value = []
    localStorage.removeItem(ACCESS_TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
  }

  async function login(): Promise<void> {
    await startLoginFlow()
  }

  async function handleCallback(code: string, state: string): Promise<void> {
    const tokens = await exchangeCodeForToken(code, state)
    storeTokens(tokens.access_token, tokens.refresh_token)
  }

  async function refresh(): Promise<void> {
    if (!refreshToken.value) throw new Error('No refresh token available')
    const tokens = await refreshAccessToken(refreshToken.value)
    storeTokens(tokens.access_token, tokens.refresh_token)
  }

  function logout(): void {
    const logoutUrl = buildLogoutUrl()
    clearTokens()
    window.location.href = logoutUrl
  }

  return {
    accessToken,
    refreshToken,
    user,
    roles,
    isAuthenticated,
    isAdmin,
    servicePermissions,
    login,
    handleCallback,
    refresh,
    logout,
  }
})
