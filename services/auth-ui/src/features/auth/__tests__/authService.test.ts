import { beforeEach, describe, expect, it, vi } from 'vitest'

const mockFetch = vi.fn()
globalThis.fetch = mockFetch

vi.mock('@personal-stack/vue-common', () => ({
  useAuth: () => ({
    getCsrfToken: () => 'fake-csrf-token',
    setUser: vi.fn(),
    fetchUser: vi.fn().mockResolvedValue(null),
    logout: vi.fn(),
    user: null,
    isAuthenticated: false,
  }),
}))

// Import after mocks are set up
const authService = await import('../services/authService')
const { register, sessionLogin, confirmEmail, resendConfirmation, enrollTotp, verifyTotp } = authService

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

  it('register sends registration data', async () => {
    mockFetch.mockResolvedValue(jsonResponse(undefined, 204))

    await register({ username: 'bob', email: 'bob@example.com', password: 'pass1234' })

    expect(mockFetch).toHaveBeenCalledWith(
      '/api/v1/users/register',
      expect.objectContaining({
        method: 'POST',
        credentials: 'include',
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

    expect(mockFetch).toHaveBeenCalledWith(
      '/api/v1/auth/confirm-email?token=abc-123',
      expect.objectContaining({
        credentials: 'include',
      }),
    )
    expect(result.message).toBe('Email confirmed')
  })

  it('resendConfirmation sends email via POST', async () => {
    const responseData = { message: 'Confirmation sent' }
    mockFetch.mockResolvedValue(jsonResponse(responseData))

    const result = await resendConfirmation('alice@example.com')

    expect(mockFetch).toHaveBeenCalledWith(
      '/api/v1/auth/resend-confirmation',
      expect.objectContaining({
        method: 'POST',
        credentials: 'include',
        body: JSON.stringify({ email: 'alice@example.com' }),
      }),
    )
    expect(result.message).toBe('Confirmation sent')
  })

  it('enrollTotp sends authenticated POST with CSRF header', async () => {
    const responseData = { secret: 'ABCDEF', qrUri: 'otpauth://totp/test' }
    mockFetch.mockResolvedValue(jsonResponse(responseData))

    const result = await enrollTotp()

    expect(mockFetch).toHaveBeenCalledWith(
      '/api/v1/totp/enroll',
      expect.objectContaining({
        method: 'POST',
        credentials: 'include',
        headers: expect.objectContaining({ 'X-XSRF-TOKEN': 'fake-csrf-token' }),
      }),
    )
    expect(result.secret).toBe('ABCDEF')
  })

  it('verifyTotp sends authenticated POST with CSRF header and code', async () => {
    mockFetch.mockResolvedValue(jsonResponse(undefined, 204))

    await verifyTotp('654321')

    expect(mockFetch).toHaveBeenCalledWith(
      '/api/v1/totp/verify',
      expect.objectContaining({
        method: 'POST',
        credentials: 'include',
        headers: expect.objectContaining({ 'X-XSRF-TOKEN': 'fake-csrf-token' }),
        body: JSON.stringify({ code: '654321' }),
      }),
    )
  })

  it('sessionLogin includes totpCode when provided', async () => {
    const responseData = { success: true, totpRequired: false }
    mockFetch.mockResolvedValue(jsonResponse(responseData))

    await sessionLogin('alice', 'secret', '123456')

    expect(mockFetch).toHaveBeenCalledWith(
      '/api/v1/auth/session-login',
      expect.objectContaining({
        body: JSON.stringify({ username: 'alice', password: 'secret', totpCode: '123456' }),
      }),
    )
  })

  it('sessionLogin throws on non-ok response', async () => {
    const errorData = { title: 'Unauthorized', status: 401 }
    mockFetch.mockResolvedValue(jsonResponse(errorData, 401))

    await expect(sessionLogin('alice', 'wrong')).rejects.toEqual(errorData)
  })

  it('confirmEmail throws on non-ok response', async () => {
    const errorData = { title: 'Gone', status: 410 }
    mockFetch.mockResolvedValue(jsonResponse(errorData, 410))

    await expect(confirmEmail('expired-token')).rejects.toEqual(errorData)
  })

  it('register throws on non-ok response', async () => {
    const errorData = { title: 'Bad Request', status: 400, detail: 'Invalid input' }
    mockFetch.mockResolvedValue(jsonResponse(errorData, 400))

    await expect(register({ username: 'x', email: 'x@x.com', password: 'y' })).rejects.toEqual(errorData)
  })
})
