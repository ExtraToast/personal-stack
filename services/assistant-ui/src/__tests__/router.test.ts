import { describe, expect, it, vi } from 'vitest'

vi.mock('@personal-stack/vue-common', () => ({
  useAuth: () => ({
    isAuthenticated: { value: false },
    user: { value: null },
    getAccessToken: () => null,
  }),
}))

describe('router', () => {
  it('has /chat route requiring auth', async () => {
    const { router } = await import('../router/index')
    const chatRoute = router.getRoutes().find((r) => r.path === '/chat')
    expect(chatRoute).toBeDefined()
    expect(chatRoute?.meta?.requiresAuth).toBe(true)
  })

  it('redirects / to /chat', async () => {
    const { router } = await import('../router/index')
    const resolved = router.resolve('/')
    expect(resolved.redirectedFrom).toBeUndefined()
  })

  it('chat route is named chat', async () => {
    const { router } = await import('../router/index')
    const chatRoute = router.getRoutes().find((r) => r.name === 'chat')
    expect(chatRoute).toBeDefined()
    expect(chatRoute?.path).toBe('/chat')
  })
})
