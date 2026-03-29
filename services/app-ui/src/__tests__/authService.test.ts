import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  buildLogoutUrl,
  exchangeCodeForToken,
  generateCodeChallenge,
  generateCodeVerifier,
  refreshAccessToken,
  startLoginFlow,
} from '../features/auth/services/authService'

describe('generateCodeVerifier', () => {
  it('produces a non-empty base64url string', () => {
    const verifier = generateCodeVerifier()
    expect(verifier).toBeTruthy()
    expect(verifier).toMatch(/^[\w-]+$/u)
  })

  it('produces unique values on each call', () => {
    const v1 = generateCodeVerifier()
    const v2 = generateCodeVerifier()
    expect(v1).not.toBe(v2)
  })
})

describe('generateCodeChallenge', () => {
  it('produces a non-empty base64url string for a given verifier', async () => {
    const verifier = generateCodeVerifier()
    const challenge = await generateCodeChallenge(verifier)
    expect(challenge).toBeTruthy()
    expect(challenge).toMatch(/^[\w-]+$/u)
  })

  it('produces a deterministic challenge for the same verifier', async () => {
    const verifier = 'test-verifier-12345'
    const c1 = await generateCodeChallenge(verifier)
    const c2 = await generateCodeChallenge(verifier)
    expect(c1).toBe(c2)
  })
})

describe('startLoginFlow', () => {
  const originalLocation = window.location

  beforeEach(() => {
    Object.defineProperty(window, 'location', {
      writable: true,
      value: { origin: 'http://localhost:5175', href: '' },
    })
    sessionStorage.clear()
  })

  afterEach(() => {
    Object.defineProperty(window, 'location', {
      writable: true,
      value: originalLocation,
    })
    vi.restoreAllMocks()
  })

  it('stores pkce_verifier and pkce_state in sessionStorage', async () => {
    await startLoginFlow()
    expect(sessionStorage.getItem('pkce_verifier')).toBeTruthy()
    expect(sessionStorage.getItem('pkce_state')).toBeTruthy()
  })

  it('redirects to auth server with required PKCE params', async () => {
    await startLoginFlow()
    expect(window.location.href).toContain('/oauth2/authorize')
    expect(window.location.href).toContain('code_challenge_method=S256')
    expect(window.location.href).toContain('client_id=app-ui')
    expect(window.location.href).toContain('response_type=code')
  })
})

describe('exchangeCodeForToken', () => {
  beforeEach(() => {
    sessionStorage.setItem('pkce_verifier', 'test-verifier')
    sessionStorage.setItem('pkce_state', 'test-state')
  })

  afterEach(() => {
    sessionStorage.clear()
    vi.restoreAllMocks()
  })

  it('throws on state mismatch', async () => {
    await expect(exchangeCodeForToken('code', 'wrong-state')).rejects.toThrow('State mismatch')
  })

  it('throws when no verifier is stored', async () => {
    sessionStorage.removeItem('pkce_verifier')
    await expect(exchangeCodeForToken('code', 'test-state')).rejects.toThrow('No PKCE code verifier')
  })

  it('returns token response on success', async () => {
    const mockTokens = { access_token: 'at', refresh_token: 'rt', expires_in: 900, token_type: 'Bearer' }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(mockTokens) }))

    const result = await exchangeCodeForToken('code', 'test-state')
    expect(result.access_token).toBe('at')
    expect(result.refresh_token).toBe('rt')
  })

  it('throws on non-ok fetch response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 400 }))
    await expect(exchangeCodeForToken('code', 'test-state')).rejects.toThrow('Token exchange failed')
  })
})

describe('refreshAccessToken', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('sends correct grant_type and client_id', async () => {
    const mockTokens = { access_token: 'at', refresh_token: 'rt', expires_in: 900, token_type: 'Bearer' }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(mockTokens) }))

    await refreshAccessToken('old-rt')

    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining('/oauth2/token'),
      expect.objectContaining({
        method: 'POST',
        body: expect.any(URLSearchParams),
      }),
    )
  })
})

describe('buildLogoutUrl', () => {
  const originalLocation = window.location

  beforeEach(() => {
    Object.defineProperty(window, 'location', {
      writable: true,
      value: { origin: 'http://localhost:5175', href: '' },
    })
  })

  afterEach(() => {
    Object.defineProperty(window, 'location', {
      writable: true,
      value: originalLocation,
    })
  })

  it('contains client_id parameter', () => {
    const url = buildLogoutUrl()
    expect(url).toContain('client_id=app-ui')
  })

  it('contains post_logout_redirect_uri', () => {
    const url = buildLogoutUrl()
    expect(url).toContain('post_logout_redirect_uri=')
    expect(url).toContain('http%3A%2F%2Flocalhost%3A5175')
  })
})
