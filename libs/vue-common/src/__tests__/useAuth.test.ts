import { beforeEach, describe, expect, it, vi } from 'vitest'

describe('useAuth', () => {
  beforeEach(() => {
    vi.resetModules()
    // Clear any cookies
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
  })

  it('starts unauthenticated when no user is set', async () => {
    const { useAuth } = await import('../composables/useAuth')
    const { isAuthenticated } = useAuth()
    expect(isAuthenticated.value).toBe(false)
  })

  it('setUser sets user and isAuthenticated becomes true', async () => {
    const { useAuth } = await import('../composables/useAuth')
    const { setUser, isAuthenticated, user } = useAuth()

    setUser({ id: 'user-123', username: 'alice', email: '', role: 'USER' })

    expect(isAuthenticated.value).toBe(true)
    expect(user.value?.username).toBe('alice')
    expect(user.value?.role).toBe('USER')
  })

  it('logout clears user', async () => {
    const { useAuth } = await import('../composables/useAuth')
    const { setUser, logout, isAuthenticated } = useAuth()

    setUser({ id: 'user-123', username: 'alice', email: '', role: 'USER' })
    logout()

    expect(isAuthenticated.value).toBe(false)
    expect(useAuth().user.value).toBeNull()
  })

  it('fetchUser calls /auth/me and sets user on success', async () => {
    const mockUser = { id: 'user-1', username: 'bob', role: 'ADMIN' }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(mockUser) }))

    const { useAuth } = await import('../composables/useAuth')
    const { fetchUser, user } = useAuth()

    const result = await fetchUser()

    expect(fetch).toHaveBeenCalledWith(expect.stringContaining('/auth/me'), { credentials: 'include' })
    expect(result?.username).toBe('bob')
    expect(user.value?.username).toBe('bob')

    vi.restoreAllMocks()
  })

  it('fetchUser returns null and clears user on failure', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 401 }))

    const { useAuth } = await import('../composables/useAuth')
    const { fetchUser, setUser, user } = useAuth()

    setUser({ id: 'user-1', username: 'alice', email: '', role: 'USER' })
    const result = await fetchUser()

    expect(result).toBeNull()
    expect(user.value).toBeNull()

    vi.restoreAllMocks()
  })

  it('getCsrfToken reads XSRF-TOKEN cookie', async () => {
    document.cookie = 'XSRF-TOKEN=abc123'

    const { useAuth } = await import('../composables/useAuth')
    const { getCsrfToken } = useAuth()

    expect(getCsrfToken()).toBe('abc123')
  })

  it('getCsrfToken returns null when no cookie', async () => {
    const { useAuth } = await import('../composables/useAuth')
    const { getCsrfToken } = useAuth()

    expect(getCsrfToken()).toBeNull()
  })
})
