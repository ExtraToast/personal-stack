export interface AuthUser {
  sub: string
  username: string
  email: string
}

export interface TokenResponse {
  access_token: string
  refresh_token: string
  expires_in: number
  token_type: string
}

export interface JwtPayload {
  sub: string
  username?: string
  email?: string
  roles?: string[]
  exp?: number
}
