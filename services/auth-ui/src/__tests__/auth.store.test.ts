import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mockSetUser = vi.fn()
const mockAuthLogout = vi.fn()
const mockFetchUser = vi.fn().mockResolvedValue(null)

vi.mock('@personal-stack/vue-common', async () => {
  const vue = await import('vue')
  return {
    useAuth: () => ({
      user: vue.ref(null),
      isAuthenticated: vue.ref(false),
      setUser: mockSetUser,
      fetchUser: mockFetchUser,
      getCsrfToken: () => 'fake-csrf-token',
      logout: mockAuthLogout,
    }),
  }
})

const mockApiSessionLogin = vi.fn()
const mockApiRegister = vi.fn()

vi.mock('@/features/auth/services/authService', () => ({
  sessionLogin: (...args: unknown[]) => mockApiSessionLogin(...args),
  register: (...args: unknown[]) => mockApiRegister(...args),
}))

const { useAuthStore } = await import('@/stores/auth')

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockApiSessionLogin.mockReset()
    mockApiRegister.mockReset()
    mockSetUser.mockReset()
    mockAuthLogout.mockReset()
    mockFetchUser.mockReset().mockResolvedValue(null)
  })

  describe('login', () => {
    it('sets user on successful login', async () => {
      mockApiSessionLogin.mockResolvedValue({
        success: true,
        totpRequired: false,
        user: { id: '1', username: 'alice', role: 'USER' },
      })

      const store = useAuthStore()
      await store.login('alice', 'password')

      expect(mockApiSessionLogin).toHaveBeenCalledWith('alice', 'password', undefined)
      expect(mockSetUser).toHaveBeenCalledWith({
        id: '1',
        username: 'alice',
        email: '',
        role: 'USER',
      })
      expect(store.isLoading).toBe(false)
    })

    it('sets totpRequired when TOTP challenge returned', async () => {
      mockApiSessionLogin.mockResolvedValue({
        success: false,
        totpRequired: true,
      })

      const store = useAuthStore()
      await store.login('alice', 'password')

      expect(store.totpRequired).toBe(true)
      expect(mockSetUser).not.toHaveBeenCalled()
    })

    it('passes totpCode when provided', async () => {
      mockApiSessionLogin.mockResolvedValue({
        success: true,
        totpRequired: false,
        user: { id: '1', username: 'alice', role: 'USER' },
      })

      const store = useAuthStore()
      await store.login('alice', 'password', '123456')

      expect(mockApiSessionLogin).toHaveBeenCalledWith('alice', 'password', '123456')
    })

    it('sets error on login failure with ProblemDetail', async () => {
      mockApiSessionLogin.mockRejectedValue({ title: 'Unauthorized', status: 401, detail: 'Bad credentials' })

      const store = useAuthStore()
      await expect(store.login('alice', 'wrong')).rejects.toBeTruthy()

      expect(store.error).toBe('Bad credentials')
      expect(store.isLoading).toBe(false)
    })

    it('sets generic error on non-ProblemDetail failure', async () => {
      mockApiSessionLogin.mockRejectedValue(new Error('network'))

      const store = useAuthStore()
      await expect(store.login('alice', 'pass')).rejects.toBeTruthy()

      expect(store.error).toBe('Login failed')
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

  describe('checkSession', () => {
    it('calls fetchUser', async () => {
      const store = useAuthStore()
      await store.checkSession()

      expect(mockFetchUser).toHaveBeenCalled()
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
