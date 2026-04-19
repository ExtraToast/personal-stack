import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../features/auth/stores/auth'

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    Object.defineProperty(window, 'location', {
      writable: true,
      value: { origin: 'http://localhost:5175', href: '' },
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('is not authenticated when no session is loaded', () => {
    const store = useAuthStore()
    expect(store.isAuthenticated).toBe(false)
    expect(store.user).toBeNull()
  })

  it('fetchSession sets user and roles on success', async () => {
    const mockUser = {
      sub: 'user-1',
      username: 'alice',
      email: 'alice@example.com',
      roles: ['ROLE_USER', 'SERVICE_GRAFANA'],
    }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(mockUser) }))

    const store = useAuthStore()
    await store.fetchSession()

    expect(store.isAuthenticated).toBe(true)
    expect(store.user?.username).toBe('alice')
    expect(store.servicePermissions).toContain('GRAFANA')
  })

  it('isAdmin is true when ROLE_ADMIN is in roles', async () => {
    const mockUser = { sub: 'admin-1', username: 'admin', email: 'admin@example.com', roles: ['ROLE_ADMIN'] }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(mockUser) }))

    const store = useAuthStore()
    await store.fetchSession()

    expect(store.isAdmin).toBe(true)
  })

  it('isAdmin is false when no ROLE_ADMIN in roles', async () => {
    const mockUser = {
      sub: 'u1',
      username: 'alice',
      email: 'alice@example.com',
      roles: ['ROLE_USER', 'SERVICE_GRAFANA'],
    }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(mockUser) }))

    const store = useAuthStore()
    await store.fetchSession()

    expect(store.isAdmin).toBe(false)
  })

  it('logout clears user state and redirects', async () => {
    const mockUser = { sub: 'user-1', username: 'alice', email: 'alice@example.com', roles: ['ROLE_USER'] }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(mockUser) }))

    const store = useAuthStore()
    await store.fetchSession()

    store.logout()

    expect(store.isAuthenticated).toBe(false)
    expect(store.user).toBeNull()
    expect(window.location.href).toContain('/api/v1/auth/logout')
  })

  it('servicePermissions filters only SERVICE_ prefixed roles', async () => {
    const mockUser = {
      sub: 'u1',
      username: 'alice',
      email: 'alice@example.com',
      roles: ['ROLE_USER', 'ROLE_ADMIN', 'SERVICE_GRAFANA', 'SERVICE_VAULT'],
    }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(mockUser) }))

    const store = useAuthStore()
    await store.fetchSession()

    expect(store.servicePermissions).toEqual(['GRAFANA', 'VAULT'])
    expect(store.servicePermissions).not.toContain('USER')
    expect(store.servicePermissions).not.toContain('ADMIN')
  })

  it('fetchSession throws on failed session check', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 401 }))

    const store = useAuthStore()
    await expect(store.fetchSession()).rejects.toThrow('Session check failed')
  })
})
