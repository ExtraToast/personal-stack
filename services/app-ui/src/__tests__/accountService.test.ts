import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  changePassword,
  fetchProfile,
  forgotPassword,
  resetPassword,
  updateProfile,
} from '../features/account/services/accountService'
import { authedFetch } from '../lib/authedFetch'

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

function lastRequestHeaders(): Headers {
  const call = vi.mocked(fetch).mock.calls.at(-1)
  expect(call).toBeDefined()

  const headers = call?.[1]?.headers
  expect(headers).toBeInstanceOf(Headers)
  if (!(headers instanceof Headers)) {
    throw new TypeError('Expected fetch call to use Headers')
  }

  return headers
}

afterEach(() => {
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
  vi.restoreAllMocks()
})

describe('fetchProfile', () => {
  it('fetches from /api/v1/users/me', async () => {
    const profile = { id: '1', username: 'alice', email: 'alice@example.com', firstName: 'Alice', lastName: 'Smith' }
    mockFetch(profile)

    const result = await fetchProfile()

    expect(result.username).toBe('alice')
    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining('/api/v1/users/me'),
      expect.objectContaining({ credentials: 'include' }),
    )
  })
})

describe('updateProfile', () => {
  it('sends PATCH with firstName/lastName', async () => {
    const updated = { id: '1', username: 'alice', email: 'alice@example.com', firstName: 'Bob', lastName: 'Jones' }
    mockFetch(updated)

    const result = await updateProfile({ firstName: 'Bob', lastName: 'Jones' })

    expect(result.firstName).toBe('Bob')
    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining('/api/v1/users/me'),
      expect.objectContaining({ method: 'PATCH', credentials: 'include' }),
    )
  })
})

describe('changePassword', () => {
  it('sends POST with passwords', async () => {
    mockFetch(null)

    await changePassword({ currentPassword: 'old123', newPassword: 'new456' })

    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining('/api/v1/auth/change-password'),
      expect.objectContaining({ method: 'POST', credentials: 'include' }),
    )
  })
})

describe('forgotPassword', () => {
  it('sends POST with email', async () => {
    mockFetch(null)

    await forgotPassword('alice@example.com')

    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining('/api/v1/auth/forgot-password'),
      expect.objectContaining({ method: 'POST', credentials: 'include' }),
    )
  })
})

describe('resetPassword', () => {
  it('sends POST with token and newPassword', async () => {
    mockFetch(null)

    await resetPassword('reset-token-123', 'newPass456')

    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining('/api/v1/auth/reset-password'),
      expect.objectContaining({ method: 'POST', credentials: 'include' }),
    )
  })
})

describe('authedFetch', () => {
  it('includes CSRF token for POST requests', async () => {
    document.cookie = 'XSRF-TOKEN=test-csrf-token; path=/'
    mockFetch({ ok: true })

    await authedFetch('https://auth.jorisjonkers.dev/api/v1/test', { method: 'POST' })

    expect(lastRequestHeaders().get('X-XSRF-TOKEN')).toBe('test-csrf-token')
  })

  it('throws on non-ok response', async () => {
    mockFetch({}, false, 403)

    await expect(authedFetch('https://auth.jorisjonkers.dev/api/v1/test')).rejects.toThrow('Request failed: 403')
  })
})
