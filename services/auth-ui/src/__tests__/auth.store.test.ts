import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mockSetTokens = vi.fn()
const mockAuthLogout = vi.fn()
const mockGetAccessToken = vi.fn().mockReturnValue('access-token')
const mockGetRefreshToken = vi.fn().mockReturnValue('refresh-token')

vi.mock('@personal-stack/vue-common', async () => {
  const vue = await import('vue')
  return {
    useAuth: () => ({
      user: vue.ref(null),
      isAuthenticated: vue.ref(false),
      setTokens: mockSetTokens,
      getAccessToken: mockGetAccessToken,
      getRefreshToken: mockGetRefreshToken,
      logout: mockAuthLogout,
    }),
  }
})

const mockApiLogin = vi.fn()
const mockApiRefresh = vi.fn()
const mockApiRegister = vi.fn()
const mockApiTotpChallenge = vi.fn()

vi.mock('@/features/auth/services/authService', () => ({
  login: (...args: unknown[]) => mockApiLogin(...args),
  refresh: (...args: unknown[]) => mockApiRefresh(...args),
  register: (...args: unknown[]) => mockApiRegister(...args),
  submitTotpChallenge: (...args: unknown[]) => mockApiTotpChallenge(...args),
}))

const { useAuthStore } = await import('@/stores/auth')

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockApiLogin.mockReset()
    mockApiRefresh.mockReset()
    mockApiRegister.mockReset()
    mockApiTotpChallenge.mockReset()
    mockSetTokens.mockReset()
    mockAuthLogout.mockReset()
    mockGetRefreshToken.mockReset().mockReturnValue('refresh-token')
  })

  describe('login', () => {
    it('sets tokens on successful login', async () => {
      mockApiLogin.mockResolvedValue({
        totpRequired: false,
        accessToken: 'at',
        refreshToken: 'rt',
        expiresIn: 900,
      })

      const store = useAuthStore()
      await store.login('alice', 'password')

      expect(mockApiLogin).toHaveBeenCalledWith({ username: 'alice', password: 'password' })
      expect(mockSetTokens).toHaveBeenCalledWith({
        accessToken: 'at',
        refreshToken: 'rt',
        expiresIn: 900,
      })
      expect(store.isLoading).toBe(false)
    })

    it('sets totpRequired when TOTP challenge returned', async () => {
      mockApiLogin.mockResolvedValue({
        totpRequired: true,
        totpChallengeToken: 'challenge-123',
      })

      const store = useAuthStore()
      await store.login('alice', 'password')

      expect(store.totpRequired).toBe(true)
      expect(mockSetTokens).not.toHaveBeenCalled()
    })

    it('sets error on login failure with ProblemDetail', async () => {
      mockApiLogin.mockRejectedValue({ title: 'Unauthorized', status: 401, detail: 'Bad credentials' })

      const store = useAuthStore()
      await expect(store.login('alice', 'wrong')).rejects.toBeTruthy()

      expect(store.error).toBe('Bad credentials')
      expect(store.isLoading).toBe(false)
    })

    it('sets generic error on non-ProblemDetail failure', async () => {
      mockApiLogin.mockRejectedValue(new Error('network'))

      const store = useAuthStore()
      await expect(store.login('alice', 'pass')).rejects.toBeTruthy()

      expect(store.error).toBe('Login failed')
    })

    it('uses expiresIn default of 900 when not provided', async () => {
      mockApiLogin.mockResolvedValue({
        totpRequired: false,
        accessToken: 'at',
        refreshToken: 'rt',
      })

      const store = useAuthStore()
      await store.login('alice', 'password')

      expect(mockSetTokens).toHaveBeenCalledWith({
        accessToken: 'at',
        refreshToken: 'rt',
        expiresIn: 900,
      })
    })
  })

  describe('verifyTotpChallenge', () => {
    it('verifies TOTP and sets tokens', async () => {
      mockApiLogin.mockResolvedValue({
        totpRequired: true,
        totpChallengeToken: 'challenge-123',
      })
      mockApiTotpChallenge.mockResolvedValue({
        accessToken: 'at',
        refreshToken: 'rt',
        expiresIn: 900,
      })

      const store = useAuthStore()
      await store.login('alice', 'password')
      await store.verifyTotpChallenge('123456')

      expect(mockApiTotpChallenge).toHaveBeenCalledWith('challenge-123', '123456')
      expect(mockSetTokens).toHaveBeenCalledWith({
        accessToken: 'at',
        refreshToken: 'rt',
        expiresIn: 900,
      })
      expect(store.totpRequired).toBe(false)
    })

    it('throws error when no TOTP challenge in progress', async () => {
      const store = useAuthStore()
      await expect(store.verifyTotpChallenge('123456')).rejects.toThrow('No TOTP challenge token')
      expect(store.error).toBe('No TOTP challenge in progress')
    })

    it('sets error on TOTP verification failure', async () => {
      mockApiLogin.mockResolvedValue({
        totpRequired: true,
        totpChallengeToken: 'challenge-123',
      })
      mockApiTotpChallenge.mockRejectedValue({ title: 'Bad Request', status: 400, detail: 'Invalid TOTP code' })

      const store = useAuthStore()
      await store.login('alice', 'password')
      await expect(store.verifyTotpChallenge('000000')).rejects.toBeTruthy()

      expect(store.error).toBe('Invalid TOTP code')
    })
  })

  describe('register', () => {
    it('calls apiRegister with correct data', async () => {
      mockApiRegister.mockResolvedValue(undefined)

      const store = useAuthStore()
      await store.register('bob', 'bob@example.com', 'pass1234')

      expect(mockApiRegister).toHaveBeenCalledWith({
        username: 'bob',
        email: 'bob@example.com',
        password: 'pass1234',
      })
      expect(store.isLoading).toBe(false)
    })

    it('sets error on registration failure', async () => {
      mockApiRegister.mockRejectedValue({ title: 'Conflict', status: 409, detail: 'Username taken' })

      const store = useAuthStore()
      await expect(store.register('bob', 'bob@example.com', 'pass')).rejects.toBeTruthy()

      expect(store.error).toBe('Username taken')
    })
  })

  describe('refreshTokens', () => {
    it('refreshes tokens successfully', async () => {
      const tokens = { accessToken: 'new-at', refreshToken: 'new-rt', expiresIn: 900 }
      mockApiRefresh.mockResolvedValue(tokens)

      const store = useAuthStore()
      const result = await store.refreshTokens()

      expect(result).toBe(true)
      expect(mockApiRefresh).toHaveBeenCalledWith('refresh-token')
      expect(mockSetTokens).toHaveBeenCalledWith(tokens)
    })

    it('returns false when no refresh token', async () => {
      mockGetRefreshToken.mockReturnValue(null)

      const store = useAuthStore()
      const result = await store.refreshTokens()

      expect(result).toBe(false)
      expect(mockApiRefresh).not.toHaveBeenCalled()
    })

    it('logs out on refresh failure', async () => {
      mockApiRefresh.mockRejectedValue(new Error('expired'))

      const store = useAuthStore()
      const result = await store.refreshTokens()

      expect(result).toBe(false)
      expect(mockAuthLogout).toHaveBeenCalled()
    })
  })

  describe('logout', () => {
    it('calls authLogout and resets TOTP state', () => {
      const store = useAuthStore()
      store.logout()

      expect(mockAuthLogout).toHaveBeenCalled()
      expect(store.totpRequired).toBe(false)
    })
  })
})
