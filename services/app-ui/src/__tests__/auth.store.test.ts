import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../features/auth/stores/auth'

// A minimal JWT with roles claim (header.payload.signature — signature is fake but ignored by parseJwtPayload)
function makeJwt(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: 'RS256', typ: 'JWT' }))
  const body = btoa(JSON.stringify(payload))
  return `${header}.${body}.signature`
}

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    Object.defineProperty(window, 'location', {
      writable: true,
      value: { origin: 'http://localhost:5175', href: '' },
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('is not authenticated when no token is stored', () => {
    const store = useAuthStore()
    expect(store.isAuthenticated).toBe(false)
    expect(store.user).toBeNull()
  })

  it('reads token from localStorage on init', () => {
    const token = makeJwt({ sub: 'user-1', username: 'alice', roles: ['ROLE_USER', 'SERVICE_GRAFANA'] })
    localStorage.setItem('access_token', token)
    localStorage.setItem('refresh_token', 'rt')

    const store = useAuthStore()
    expect(store.isAuthenticated).toBe(true)
    expect(store.user?.username).toBe('alice')
    expect(store.servicePermissions).toContain('GRAFANA')
  })

  it('isAdmin is true when ROLE_ADMIN is in roles', () => {
    const token = makeJwt({ sub: 'admin-1', username: 'admin', roles: ['ROLE_ADMIN'] })
    localStorage.setItem('access_token', token)
    localStorage.setItem('refresh_token', 'rt')

    const store = useAuthStore()
    expect(store.isAdmin).toBe(true)
  })

  it('logout clears tokens and redirects', () => {
    const token = makeJwt({ sub: 'user-1', username: 'alice', roles: ['ROLE_USER'] })
    localStorage.setItem('access_token', token)
    localStorage.setItem('refresh_token', 'rt')

    const store = useAuthStore()
    store.logout()

    expect(store.isAuthenticated).toBe(false)
    expect(store.user).toBeNull()
    expect(localStorage.getItem('access_token')).toBeNull()
    expect(localStorage.getItem('refresh_token')).toBeNull()
  })

  it('handleCallback stores tokens after successful exchange', async () => {
    const accessToken = makeJwt({ sub: 'u1', username: 'bob', roles: ['ROLE_USER'] })
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: () =>
          Promise.resolve({
            access_token: accessToken,
            refresh_token: 'new-rt',
            expires_in: 900,
            token_type: 'Bearer',
          }),
      }),
    )
    sessionStorage.setItem('pkce_verifier', 'verifier')
    sessionStorage.setItem('pkce_state', 'state-123')

    const store = useAuthStore()
    await store.handleCallback('auth-code', 'state-123')

    expect(store.isAuthenticated).toBe(true)
    expect(store.user?.username).toBe('bob')
    expect(localStorage.getItem('refresh_token')).toBe('new-rt')
  })
})
