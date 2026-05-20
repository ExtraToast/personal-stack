import { describe, expect, it, vi } from 'vitest'

vi.mock('@personal-stack/vue-common', () => ({
  useAuth: () => ({
    isAuthenticated: { value: false },
    user: { value: null },
    getAccessToken: () => null,
  }),
}))

describe('router', () => {
  it('has /sessions route requiring auth', async () => {
    const { router } = await import('../router/index')
    const sessionsRoute = router.getRoutes().find((r) => r.path === '/sessions')
    expect(sessionsRoute).toBeDefined()
    expect(sessionsRoute?.meta?.requiresAuth).toBe(true)
  })

  it('keeps /chat as a backwards-compat redirect to /sessions', async () => {
    const { router } = await import('../router/index')
    const chatRoute = router.getRoutes().find((r) => r.path === '/chat')
    expect(chatRoute).toBeDefined()
    expect(chatRoute?.redirect).toBe('/sessions')
  })

  it('sessions route is named sessions', async () => {
    const { router } = await import('../router/index')
    const sessionsRoute = router.getRoutes().find((r) => r.name === 'sessions')
    expect(sessionsRoute).toBeDefined()
    expect(sessionsRoute?.path).toBe('/sessions')
  })
})
