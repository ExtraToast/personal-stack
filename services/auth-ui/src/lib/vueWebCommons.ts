import type { AuthApi, ThemeApi, ThemeMode, User, UseThemeOptions } from '@extratoast/vue-web-commons'
import {
  cookieCsrfTokenSource,
  useAuth as useCommonsAuth,
  useTheme as useCommonsTheme,
} from '@extratoast/vue-web-commons'

export * from '@extratoast/vue-web-commons'

const validRoles = ['ADMIN', 'USER', 'READONLY'] as const
type PersonalStackRole = (typeof validRoles)[number]

interface SessionUserPayload {
  id?: string
  username?: string
  name?: string
  email?: string
  role?: string
}

export const personalStackThemeOptions = {
  storageKey: 'ps_theme',
  defaultMode: 'system',
  allowedModes: ['light', 'dark', 'system'],
  target: () => (typeof document === 'undefined' ? null : document.documentElement),
  attribute: 'data-theme',
  className: 'dark',
} satisfies UseThemeOptions<ThemeMode>

export function useAuth(): AuthApi<User<PersonalStackRole>> {
  return useCommonsAuth<SessionUserPayload, User<PersonalStackRole>>({
    currentUserUrl: '/api/v1/auth/me',
    credentials: 'include',
    csrfTokenSource,
    mapUser,
  })
}

export function useTheme(): ThemeApi<ThemeMode> {
  return useCommonsTheme(personalStackThemeOptions)
}

function csrfTokenSource(): string | null {
  if (typeof document === 'undefined') return null
  return cookieCsrfTokenSource('XSRF-TOKEN', document)()
}

function mapUser(payload: SessionUserPayload): User<PersonalStackRole> {
  const role = validRoles.find((candidate) => candidate === payload.role) ?? 'USER'
  return {
    id: payload.id ?? '',
    username: payload.username ?? payload.name ?? '',
    email: payload.email ?? '',
    role,
  }
}
