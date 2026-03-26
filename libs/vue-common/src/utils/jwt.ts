export interface JwtPayload {
  sub: string
  iss?: string
  iat: number
  exp: number
  username?: string
  roles?: string[]
  type?: string
}

export function decodeJwt(token: string): JwtPayload {
  const parts = token.split('.')
  if (parts.length !== 3) {
    throw new Error('Invalid JWT format')
  }
  const base64 = parts[1]!.replace(/-/g, '+').replace(/_/g, '/')
  const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4)
  const payload: JwtPayload = JSON.parse(atob(padded))
  return payload
}

export function isTokenExpired(token: string): boolean {
  try {
    const payload = decodeJwt(token)
    // Add 10 second buffer to avoid edge cases
    return Date.now() >= (payload.exp - 10) * 1000
  } catch {
    return true
  }
}
