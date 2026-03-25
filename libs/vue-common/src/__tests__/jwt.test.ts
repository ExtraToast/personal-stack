import { describe, expect, it } from 'vitest'
import { decodeJwt, isTokenExpired } from '../utils/jwt'

// A real-looking JWT payload (not cryptographically valid — just for testing decode)
function makeTestToken(payload: object, expOffset = 3600): string {
  const header = btoa(JSON.stringify({ alg: 'RS256', typ: 'JWT' }))
  const body = btoa(
    JSON.stringify({
      iat: Math.floor(Date.now() / 1000),
      exp: Math.floor(Date.now() / 1000) + expOffset,
      ...payload,
    }),
  )
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=/g, '')
  return `${header}.${body}.fakesignature`
}

describe('decodeJwt', () => {
  it('decodes a valid JWT payload', () => {
    const token = makeTestToken({ sub: 'user-123', username: 'alice', roles: ['ROLE_USER'] })
    const payload = decodeJwt(token)
    expect(payload.sub).toBe('user-123')
    expect(payload.username).toBe('alice')
    expect(payload.roles).toEqual(['ROLE_USER'])
  })

  it('throws for invalid JWT format', () => {
    expect(() => decodeJwt('not.a.valid.jwt.token')).toThrow()
    expect(() => decodeJwt('onlytwoparts.here')).toThrow()
  })
})

describe('isTokenExpired', () => {
  it('returns false for a fresh token', () => {
    const token = makeTestToken({ sub: 'user-123' }, 3600)
    expect(isTokenExpired(token)).toBe(false)
  })

  it('returns true for an expired token', () => {
    const token = makeTestToken({ sub: 'user-123' }, -100)
    expect(isTokenExpired(token)).toBe(true)
  })

  it('returns true for a malformed token', () => {
    expect(isTokenExpired('not-a-token')).toBe(true)
  })
})
