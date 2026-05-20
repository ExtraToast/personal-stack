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

  it('redirects / to /sessions', async () => {
    const { router } = await import('../router/index')
    const rootRoute = router.getRoutes().find((r) => r.path === '/')
    expect(rootRoute).toBeDefined()
    expect(rootRoute?.redirect).toBe('/sessions')
  })

  it('exposes the workspace detail surface under /sessions/workspace/:id', async () => {
    const { router } = await import('../router/index')
    const detailRoute = router.getRoutes().find((r) => r.path === '/sessions/workspace/:id')
    expect(detailRoute).toBeDefined()
    expect(detailRoute?.name).toBe('workspace-detail')
    expect(detailRoute?.meta?.requiresAuth).toBe(true)
  })

  it('legacy /chat and /workspaces routes are gone', async () => {
    const { router } = await import('../router/index')
    const paths = router.getRoutes().map((r) => r.path)
    expect(paths).not.toContain('/chat')
    expect(paths).not.toContain('/workspaces')
    expect(paths).not.toContain('/workspaces/:id')
  })

  it('sessions route is named sessions', async () => {
    const { router } = await import('../router/index')
    const sessionsRoute = router.getRoutes().find((r) => r.name === 'sessions')
    expect(sessionsRoute).toBeDefined()
    expect(sessionsRoute?.path).toBe('/sessions')
  })
})
