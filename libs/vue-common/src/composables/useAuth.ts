import type { AuthTokens, User } from '../types'
import { computed, ref } from 'vue'
import { decodeJwt, isTokenExpired } from '../utils/jwt'

const ACCESS_TOKEN_KEY = 'ps_access_token'
const REFRESH_TOKEN_KEY = 'ps_refresh_token'

const VALID_ROLES: readonly string[] = ['ADMIN', 'USER', 'READONLY']

function isValidRole(value: string): value is User['role'] {
  return VALID_ROLES.includes(value)
}

// Module-level state — shared across all useAuth() calls (singleton pattern)
const user = ref<User | null>(null)

function userFromToken(token: string): User | null {
  try {
    const payload = decodeJwt(token)
    const roleRaw = payload.roles?.[0]?.replace('ROLE_', '') ?? 'USER'
    const role: User['role'] = isValidRole(roleRaw) ? roleRaw : 'USER'
    return {
      id: payload.sub,
      username: payload.username ?? payload.sub,
      email: '',
      role,
    }
  } catch {
    return null
  }
}

// Initialize from storage on module load
const storedToken = localStorage.getItem(ACCESS_TOKEN_KEY)
if (storedToken && !isTokenExpired(storedToken)) {
  user.value = userFromToken(storedToken)
}

// eslint-disable-next-line ts/explicit-function-return-type -- composable return type is complex and inferred
export function useAuth() {
  const isAuthenticated = computed(() => user.value !== null)

  function setTokens(tokens: AuthTokens): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken)
    localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken)
    user.value = userFromToken(tokens.accessToken)
  }

  function getAccessToken(): string | null {
    const token = localStorage.getItem(ACCESS_TOKEN_KEY)
    if (!token || isTokenExpired(token)) return null
    return token
  }

  function getRefreshToken(): string | null {
    return localStorage.getItem(REFRESH_TOKEN_KEY)
  }

  function logout(): void {
    localStorage.removeItem(ACCESS_TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
    user.value = null
  }

  return {
    user,
    isAuthenticated,
    setTokens,
    getAccessToken,
    getRefreshToken,
    logout,
  }
}
