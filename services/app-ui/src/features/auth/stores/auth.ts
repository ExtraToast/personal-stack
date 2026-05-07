import type { AuthUser } from '../types'
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { buildLogoutUrl, checkSession, startLoginFlow } from '../services/authService'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<AuthUser | null>(null)
  const roles = ref<string[]>([])
  // True once an initSession() attempt has resolved (success or failure).
  // Views that gate redirects on auth state must await initSession() before
  // deciding, so a slow/cold-start /me does not eagerly bounce signed-in
  // users back to the home page.
  const sessionInitialized = ref(false)

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

  // Memoised, error-swallowing variant for app bootstrap and view-level
  // gates. Multiple concurrent callers share the same in-flight promise;
  // once it resolves sessionInitialized flips to true and subsequent calls
  // return immediately.
  let inflight: Promise<void> | null = null
  function initSession(): Promise<void> {
    if (sessionInitialized.value) return Promise.resolve()
    if (inflight) return inflight
    inflight = fetchSession()
      .catch(() => undefined)
      .finally(() => {
        sessionInitialized.value = true
        inflight = null
      })
    return inflight
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
    sessionInitialized,
    isAuthenticated,
    isAdmin,
    servicePermissions,
    login,
    fetchSession,
    initSession,
    logout,
  }
})
