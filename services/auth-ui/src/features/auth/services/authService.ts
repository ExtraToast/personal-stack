import { useApi } from '@private-stack/vue-common'

interface LoginCredentials {
  username: string
  password: string
}

interface AuthResponse {
  accessToken: string
  refreshToken: string
}

const api = useApi()

export async function login(credentials: LoginCredentials): Promise<AuthResponse> {
  return api.post<AuthResponse>('/auth/login', credentials)
}

export async function refresh(refreshToken: string): Promise<AuthResponse> {
  return api.post<AuthResponse>('/auth/refresh', { refreshToken })
}
