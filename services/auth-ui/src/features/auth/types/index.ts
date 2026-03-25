export interface LoginCredentials {
  username: string
  password: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
}
