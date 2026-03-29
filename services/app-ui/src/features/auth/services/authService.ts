import type { TokenResponse } from '../types'

const AUTH_BASE_URL: string = import.meta.env.VITE_AUTH_URL ?? 'https://auth.jorisjonkers.dev'
const CLIENT_ID = 'app-ui'

export function getRedirectUri(): string {
  return `${window.location.origin}/callback`
}

export function generateCodeVerifier(): string {
  const array = new Uint8Array(32)
  crypto.getRandomValues(array)
  return btoa(String.fromCharCode(...array))
    .replace(/\+/gu, '-')
    .replace(/\//gu, '_')
    .replace(/=/gu, '')
}

export async function generateCodeChallenge(verifier: string): Promise<string> {
  const encoder = new TextEncoder()
  const data = encoder.encode(verifier)
  const digest = await crypto.subtle.digest('SHA-256', data)
  return btoa(String.fromCharCode(...new Uint8Array(digest)))
    .replace(/\+/gu, '-')
    .replace(/\//gu, '_')
    .replace(/=/gu, '')
}

export async function startLoginFlow(): Promise<void> {
  const verifier = generateCodeVerifier()
  const challenge = await generateCodeChallenge(verifier)
  const state = crypto.randomUUID()

  sessionStorage.setItem('pkce_verifier', verifier)
  sessionStorage.setItem('pkce_state', state)

  const params = new URLSearchParams({
    response_type: 'code',
    client_id: CLIENT_ID,
    redirect_uri: getRedirectUri(),
    scope: 'openid profile email',
    code_challenge: challenge,
    code_challenge_method: 'S256',
    state,
  })

  window.location.href = `${AUTH_BASE_URL}/api/oauth2/authorize?${params}`
}

export async function exchangeCodeForToken(code: string, state: string): Promise<TokenResponse> {
  const storedState = sessionStorage.getItem('pkce_state')
  const verifier = sessionStorage.getItem('pkce_verifier')

  if (state !== storedState) throw new Error('State mismatch — possible CSRF attack')
  if (!verifier) throw new Error('No PKCE code verifier found')

  sessionStorage.removeItem('pkce_state')
  sessionStorage.removeItem('pkce_verifier')

  const body = new URLSearchParams({
    grant_type: 'authorization_code',
    code,
    redirect_uri: getRedirectUri(),
    client_id: CLIENT_ID,
    code_verifier: verifier,
  })

  const response = await fetch(`${AUTH_BASE_URL}/api/oauth2/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  })

  if (!response.ok) throw new Error(`Token exchange failed: ${response.status}`)
  const tokens: TokenResponse = await response.json()
  return tokens
}

export async function refreshAccessToken(refreshToken: string): Promise<TokenResponse> {
  const body = new URLSearchParams({
    grant_type: 'refresh_token',
    refresh_token: refreshToken,
    client_id: CLIENT_ID,
  })

  const response = await fetch(`${AUTH_BASE_URL}/api/oauth2/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  })

  if (!response.ok) throw new Error(`Token refresh failed: ${response.status}`)
  const tokens: TokenResponse = await response.json()
  return tokens
}

export function buildLogoutUrl(): string {
  const params = new URLSearchParams({
    post_logout_redirect_uri: window.location.origin,
    client_id: CLIENT_ID,
  })
  return `${AUTH_BASE_URL}/api/connect/logout?${params}`
}
