import type { AuthUser } from '../types'
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { buildLogoutUrl, checkSession, startLoginFlow } from '../services/authService'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<AuthUser | null>(null)
  const roles = ref<string[]>([])

  const isAuthenticated = computed(() => user.value !== null)
  const isAdmin = computed(() => roles.value.includes('ROLE_ADMIN'))
  const servicePermissions = computed(() =>
    roles.value.filter((r) => r.startsWith('SERVICE_')).map((r) => r.replace('SERVICE_', '')),
  )

  function login(): void {
    startLoginFlow()
  }

  async function fetchSession(): Promise<void> {
    const sessionUser = await checkSession()
    user.value = sessionUser
    roles.value = sessionUser.roles ?? []
  }

  function logout(): void {
    const logoutUrl = buildLogoutUrl()
    user.value = null
    roles.value = []
    window.location.href = logoutUrl
  }

  return {
    user,
    roles,
    isAuthenticated,
    isAdmin,
    servicePermissions,
    login,
    fetchSession,
    logout,
  }
})
