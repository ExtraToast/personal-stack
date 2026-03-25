import { beforeEach, describe, expect, it, vi } from 'vitest'

function makeTestToken(payload: object, expOffset = 3600): string {
  const header = btoa(JSON.stringify({ alg: 'RS256', typ: 'JWT' }))
  const body = btoa(
    JSON.stringify({
      iat: Math.floor(Date.now() / 1000),
      exp: Math.floor(Date.now() / 1000) + expOffset,
      ...payload,
    }),
  )
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=/g, '')
  return `${header}.${body}.fakesig`
}

describe('useAuth', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.resetModules()
  })

  it('starts unauthenticated when no stored token', async () => {
    const { useAuth } = await import('../composables/useAuth')
    const { isAuthenticated } = useAuth()
    expect(isAuthenticated.value).toBe(false)
  })

  it('setTokens stores tokens and sets user', async () => {
    const { useAuth } = await import('../composables/useAuth')
    const { setTokens, isAuthenticated, user } = useAuth()

    const tokens = {
      accessToken: makeTestToken({ sub: 'user-123', username: 'alice', roles: ['ROLE_USER'] }),
      refreshToken: makeTestToken({ sub: 'user-123', type: 'refresh' }),
      expiresIn: 900,
    }

    setTokens(tokens)

    expect(isAuthenticated.value).toBe(true)
    expect(user.value?.username).toBe('alice')
    expect(user.value?.role).toBe('USER')
  })

  it('logout clears user and storage', async () => {
    const { useAuth } = await import('../composables/useAuth')
    const { setTokens, logout, isAuthenticated } = useAuth()

    setTokens({
      accessToken: makeTestToken({ sub: 'user-123', username: 'alice' }),
      refreshToken: makeTestToken({ sub: 'user-123' }),
      expiresIn: 900,
    })

    logout()

    expect(isAuthenticated.value).toBe(false)
    expect(localStorage.getItem('ps_access_token')).toBeNull()
  })

  it('getAccessToken returns null for expired token', async () => {
    localStorage.setItem('ps_access_token', makeTestToken({ sub: 'user-123' }, -100))
    const { useAuth } = await import('../composables/useAuth')
    const { getAccessToken } = useAuth()
    expect(getAccessToken()).toBeNull()
  })
})
