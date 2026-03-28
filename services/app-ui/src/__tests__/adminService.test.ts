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

    const fetchCall = vi.mocked(fetch).mock.calls[0]
    expect(fetchCall[0]).toContain('/users')
    expect(fetchCall[1]?.headers?.get('Authorization')).toBe(`Bearer ${TOKEN}`)
  })

  it('updateUserRole sends PATCH with correct body', async () => {
    mockFetch({ id: '1', username: 'alice', role: 'ADMIN', servicePermissions: [] })
    await updateUserRole(TOKEN, '1', 'ADMIN')

    const fetchCall = vi.mocked(fetch).mock.calls[0]
    expect(fetchCall[1]?.method).toBe('PATCH')
    expect(JSON.parse(fetchCall[1]?.body)).toEqual({ role: 'ADMIN' })
  })

  it('updateUserServices sends PUT with services array', async () => {
    mockFetch({ id: '1', username: 'alice', role: 'USER', servicePermissions: ['GRAFANA', 'VAULT'] })
    await updateUserServices(TOKEN, '1', ['GRAFANA', 'VAULT'])

    const fetchCall = vi.mocked(fetch).mock.calls[0]
    expect(fetchCall[1]?.method).toBe('PUT')
    expect(JSON.parse(fetchCall[1]?.body)).toEqual({ services: ['GRAFANA', 'VAULT'] })
  })

  it('deleteUser sends DELETE request', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(null) }))
    await deleteUser(TOKEN, '42')

    const fetchCall = vi.mocked(fetch).mock.calls[0]
    expect(fetchCall[1]?.method).toBe('DELETE')
    expect(fetchCall[0]).toContain('/users/42')
  })

  it('all requests include Authorization Bearer header', async () => {
    mockFetch([])
    await fetchUsers(TOKEN)
    const call1 = vi.mocked(fetch).mock.calls[0]
    expect(call1[1]?.headers?.get('Authorization')).toBe(`Bearer ${TOKEN}`)

    mockFetch({ id: '1', username: 'alice', role: 'USER', servicePermissions: [] })
    await updateUserRole(TOKEN, '1', 'USER')
    const call2 = vi.mocked(fetch).mock.calls[0]
    expect(call2[1]?.headers?.get('Authorization')).toBe(`Bearer ${TOKEN}`)

    mockFetch({ id: '1', username: 'alice', role: 'USER', servicePermissions: [] })
    await updateUserServices(TOKEN, '1', [])
    const call3 = vi.mocked(fetch).mock.calls[0]
    expect(call3[1]?.headers?.get('Authorization')).toBe(`Bearer ${TOKEN}`)

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(null) }))
    await deleteUser(TOKEN, '1')
    const call4 = vi.mocked(fetch).mock.calls[0]
    expect(call4[1]?.headers?.get('Authorization')).toBe(`Bearer ${TOKEN}`)
  })
})
