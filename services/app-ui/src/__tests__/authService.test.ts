import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { buildLogoutUrl, checkSession, startLoginFlow } from '../features/auth/services/authService'

describe('startLoginFlow', () => {
  const originalLocation = window.location

  beforeEach(() => {
    Object.defineProperty(window, 'location', {
      writable: true,
      value: { origin: 'http://localhost:5175', href: 'http://localhost:5175/apps' },
    })
  })

  afterEach(() => {
    Object.defineProperty(window, 'location', {
      writable: true,
      value: originalLocation,
    })
    vi.restoreAllMocks()
  })

  it('redirects to auth login page with redirect parameter', () => {
    startLoginFlow()
    expect(window.location.href).toContain('/login?redirect=')
    expect(window.location.href).toContain(encodeURIComponent('http://localhost:5175/apps'))
  })
})

describe('checkSession', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('returns user on success', async () => {
    const mockUser = { sub: 'user-1', username: 'alice', email: 'alice@example.com', roles: ['ROLE_USER'] }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(mockUser) }))

    const result = await checkSession()
    expect(result.username).toBe('alice')
    expect(result.sub).toBe('user-1')
  })

  it('throws on non-ok response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 401 }))
    await expect(checkSession()).rejects.toThrow('Session check failed')
  })

  it('calls auth/me with credentials included', async () => {
    const mockUser = { sub: 'user-1', username: 'alice', email: 'alice@example.com' }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(mockUser) }))

    await checkSession()

    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining('/api/v1/auth/me'),
      expect.objectContaining({ credentials: 'include' }),
    )
  })
})

describe('buildLogoutUrl', () => {
  it('returns the auth logout endpoint', () => {
    const url = buildLogoutUrl()
    expect(url).toContain('/api/v1/auth/logout')
  })
})
