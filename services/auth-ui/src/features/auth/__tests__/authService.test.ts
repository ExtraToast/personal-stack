import { beforeEach, describe, expect, it, vi } from 'vitest'

const mockFetch = vi.fn()
globalThis.fetch = mockFetch

vi.mock('@personal-stack/vue-common', () => ({
  useAuth: () => ({
    getAccessToken: () => 'fake-token',
    getRefreshToken: () => null,
    setTokens: vi.fn(),
    logout: vi.fn(),
    user: null,
    isAuthenticated: false,
  }),
}))

// Import after mocks are set up
const { login, submitTotpChallenge, refresh, register, sessionLogin, confirmEmail } =
  await import('../services/authService')

/* eslint-disable ts/consistent-type-assertions -- test utility needs partial Response mock */
function jsonResponse(data: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(data),
  } as Response
}
/* eslint-enable ts/consistent-type-assertions */

describe('authService', () => {
  beforeEach(() => {
    mockFetch.mockReset()
  })

  it('login calls POST /api/v1/auth/login with credentials', async () => {
    const responseData = { totpRequired: false, accessToken: 'at', refreshToken: 'rt', expiresIn: 900 }
    mockFetch.mockResolvedValue(jsonResponse(responseData))

    const result = await login({ username: 'alice', password: 'secret' })

    expect(mockFetch).toHaveBeenCalledWith(
      '/api/v1/auth/login',
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({ username: 'alice', password: 'secret' }),
      }),
    )
    expect(result.accessToken).toBe('at')
  })

  it('submitTotpChallenge sends correct payload', async () => {
    const responseData = { totpRequired: false, accessToken: 'at', refreshToken: 'rt' }
    mockFetch.mockResolvedValue(jsonResponse(responseData))

    await submitTotpChallenge('challenge-token', '123456')

    expect(mockFetch).toHaveBeenCalledWith(
      '/api/v1/auth/totp-challenge',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ totpChallengeToken: 'challenge-token', code: '123456' }),
      }),
    )
  })

  it('refresh sends refresh token', async () => {
    const responseData = { accessToken: 'new-at', refreshToken: 'new-rt', expiresIn: 900 }
    mockFetch.mockResolvedValue(jsonResponse(responseData))

    await refresh('old-rt')

    expect(mockFetch).toHaveBeenCalledWith(
      '/api/v1/auth/refresh',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ refreshToken: 'old-rt' }),
      }),
    )
  })

  it('register sends registration data', async () => {
    mockFetch.mockResolvedValue(jsonResponse(undefined, 204))

    await register({ username: 'bob', email: 'bob@example.com', password: 'pass1234' })

    expect(mockFetch).toHaveBeenCalledWith(
      '/api/v1/users/register',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ username: 'bob', email: 'bob@example.com', password: 'pass1234' }),
      }),
    )
  })

  it('sessionLogin sends credentials with cookies include', async () => {
    const responseData = { success: true, totpRequired: false }
    mockFetch.mockResolvedValue(jsonResponse(responseData))

    await sessionLogin('alice', 'secret')

    expect(mockFetch).toHaveBeenCalledWith(
      '/api/v1/auth/session-login',
      expect.objectContaining({
        method: 'POST',
        credentials: 'include',
        body: JSON.stringify({ username: 'alice', password: 'secret' }),
      }),
    )
  })

  it('sessionLogin returns totpRequired when needed', async () => {
    const responseData = { success: false, totpRequired: true }
    mockFetch.mockResolvedValue(jsonResponse(responseData))

    const result = await sessionLogin('alice', 'secret')

    expect(result.totpRequired).toBe(true)
  })

  it('confirmEmail calls GET with token parameter', async () => {
    const responseData = { message: 'Email confirmed' }
    mockFetch.mockResolvedValue(jsonResponse(responseData))

    const result = await confirmEmail('abc-123')

    expect(mockFetch).toHaveBeenCalledWith('/api/v1/auth/confirm-email?token=abc-123')
    expect(result.message).toBe('Email confirmed')
  })
})
