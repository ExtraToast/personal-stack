import { describe, expect, it } from 'vitest'
import { router } from '../router'

describe('router', () => {
  it('has a home route at /', () => {
    const route = router.getRoutes().find((r) => r.path === '/')
    expect(route).toBeDefined()
    expect(route?.name).toBe('home')
  })

  it('has an apps route at /apps', () => {
    const route = router.getRoutes().find((r) => r.path === '/apps')
    expect(route).toBeDefined()
    expect(route?.name).toBe('apps')
  })
})
