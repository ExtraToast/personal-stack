import { afterEach, describe, expect, it, vi } from 'vitest'
import { deleteUser, fetchUsers, updateUserRole, updateUserServices } from '../features/admin/services/adminService'

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
    const result = await fetchUsers()
    expect(result).toHaveLength(1)
    expect(result[0]?.username).toBe('alice')
  })

  it('throws on non-ok response', async () => {
    mockFetch({}, false, 403)
    await expect(fetchUsers()).rejects.toThrow('Request failed: 403')
  })
})

describe('updateUserRole', () => {
  it('sends PATCH request and returns updated user', async () => {
    const updated = { id: '1', username: 'alice', role: 'ADMIN', servicePermissions: [] }
    mockFetch(updated)
    const result = await updateUserRole('1', 'ADMIN')
    expect(result.role).toBe('ADMIN')
  })
})

describe('updateUserServices', () => {
  it('sends PUT request and returns updated user', async () => {
    const updated = { id: '1', username: 'alice', role: 'USER', servicePermissions: ['GRAFANA'] }
    mockFetch(updated)
    const result = await updateUserServices('1', ['GRAFANA'])
    expect(result.servicePermissions).toContain('GRAFANA')
  })
})

describe('deleteUser', () => {
  it('sends DELETE request without error', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(null) }))
    await expect(deleteUser('1')).resolves.toBeUndefined()
  })

  it('throws on non-ok response', async () => {
    mockFetch({}, false, 404)
    await expect(deleteUser('1')).rejects.toThrow('Request failed: 404')
  })
})

describe('request details', () => {
  it('fetchUsers sends GET request with credentials', async () => {
    mockFetch([])
    await fetchUsers()

    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining('/users'),
      expect.objectContaining({ credentials: 'include' }),
    )
  })

  it('updateUserRole sends PATCH with correct body', async () => {
    mockFetch({ id: '1', username: 'alice', role: 'ADMIN', servicePermissions: [] })
    await updateUserRole('1', 'ADMIN')

    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining('/users/1/role'),
      expect.objectContaining({ method: 'PATCH', credentials: 'include' }),
    )
  })

  it('updateUserServices sends PUT with services array', async () => {
    mockFetch({ id: '1', username: 'alice', role: 'USER', servicePermissions: ['GRAFANA', 'VAULT'] })
    await updateUserServices('1', ['GRAFANA', 'VAULT'])

    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining('/users/1/services'),
      expect.objectContaining({ method: 'PUT', credentials: 'include' }),
    )
  })

  it('deleteUser sends DELETE request', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(null) }))
    await deleteUser('42')

    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining('/users/42'),
      expect.objectContaining({ method: 'DELETE', credentials: 'include' }),
    )
  })

  it('all requests use credentials include', async () => {
    mockFetch([])
    await fetchUsers()
    expect(fetch).toHaveBeenCalledWith(expect.any(String), expect.objectContaining({ credentials: 'include' }))

    mockFetch({ id: '1', username: 'alice', role: 'USER', servicePermissions: [] })
    await updateUserRole('1', 'USER')
    expect(fetch).toHaveBeenCalledWith(expect.any(String), expect.objectContaining({ credentials: 'include' }))

    mockFetch({ id: '1', username: 'alice', role: 'USER', servicePermissions: [] })
    await updateUserServices('1', [])
    expect(fetch).toHaveBeenCalledWith(expect.any(String), expect.objectContaining({ credentials: 'include' }))

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(null) }))
    await deleteUser('1')
    expect(fetch).toHaveBeenCalledWith(expect.any(String), expect.objectContaining({ credentials: 'include' }))
  })
})
