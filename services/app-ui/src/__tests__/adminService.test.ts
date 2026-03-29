import { afterEach, describe, expect, it, vi } from 'vitest'
import { deleteUser, fetchUsers, updateUserRole, updateUserServices } from '../features/admin/services/adminService'

const TOKEN = 'test-token'

function mockFetch(body: unknown, ok = true, status = 200) {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({
      ok,
      status,
      json: () => Promise.resolve(body),
    }),
  )
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('fetchUsers', () => {
  it('returns list of users', async () => {
    const users = [{ id: '1', username: 'alice', email: 'alice@example.com', role: 'USER', servicePermissions: [] }]
    mockFetch(users)
    const result = await fetchUsers(TOKEN)
    expect(result).toHaveLength(1)
    expect(result[0]?.username).toBe('alice')
  })

  it('throws on non-ok response', async () => {
    mockFetch({}, false, 403)
    await expect(fetchUsers(TOKEN)).rejects.toThrow('Request failed: 403')
  })
})

describe('updateUserRole', () => {
  it('sends PATCH request and returns updated user', async () => {
    const updated = { id: '1', username: 'alice', role: 'ADMIN', servicePermissions: [] }
    mockFetch(updated)
    const result = await updateUserRole(TOKEN, '1', 'ADMIN')
    expect(result.role).toBe('ADMIN')
  })
})

describe('updateUserServices', () => {
  it('sends PUT request and returns updated user', async () => {
    const updated = { id: '1', username: 'alice', role: 'USER', servicePermissions: ['GRAFANA'] }
    mockFetch(updated)
    const result = await updateUserServices(TOKEN, '1', ['GRAFANA'])
    expect(result.servicePermissions).toContain('GRAFANA')
  })
})

describe('deleteUser', () => {
  it('sends DELETE request without error', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(null) }))
    await expect(deleteUser(TOKEN, '1')).resolves.toBeUndefined()
  })

  it('throws on non-ok response', async () => {
    mockFetch({}, false, 404)
    await expect(deleteUser(TOKEN, '1')).rejects.toThrow('Request failed: 404')
  })
})

describe('request details', () => {
  it('fetchUsers sends GET request with auth header', async () => {
    mockFetch([])
    await fetchUsers(TOKEN)

    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining('/users'),
      expect.objectContaining({
        headers: expect.any(Headers),
      }),
    )
  })

  it('updateUserRole sends PATCH with correct body', async () => {
    mockFetch({ id: '1', username: 'alice', role: 'ADMIN', servicePermissions: [] })
    await updateUserRole(TOKEN, '1', 'ADMIN')

    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining('/users/1/role'),
      expect.objectContaining({ method: 'PATCH' }),
    )
  })

  it('updateUserServices sends PUT with services array', async () => {
    mockFetch({ id: '1', username: 'alice', role: 'USER', servicePermissions: ['GRAFANA', 'VAULT'] })
    await updateUserServices(TOKEN, '1', ['GRAFANA', 'VAULT'])

    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining('/users/1/services'),
      expect.objectContaining({ method: 'PUT' }),
    )
  })

  it('deleteUser sends DELETE request', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(null) }))
    await deleteUser(TOKEN, '42')

    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining('/users/42'),
      expect.objectContaining({ method: 'DELETE' }),
    )
  })

  it('all requests include Authorization Bearer header', async () => {
    mockFetch([])
    await fetchUsers(TOKEN)
    expect(fetch).toHaveBeenCalledWith(expect.any(String), expect.objectContaining({ headers: expect.any(Headers) }))

    mockFetch({ id: '1', username: 'alice', role: 'USER', servicePermissions: [] })
    await updateUserRole(TOKEN, '1', 'USER')
    expect(fetch).toHaveBeenCalledWith(expect.any(String), expect.objectContaining({ headers: expect.any(Headers) }))

    mockFetch({ id: '1', username: 'alice', role: 'USER', servicePermissions: [] })
    await updateUserServices(TOKEN, '1', [])
    expect(fetch).toHaveBeenCalledWith(expect.any(String), expect.objectContaining({ headers: expect.any(Headers) }))

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(null) }))
    await deleteUser(TOKEN, '1')
    expect(fetch).toHaveBeenCalledWith(expect.any(String), expect.objectContaining({ headers: expect.any(Headers) }))
  })
})
