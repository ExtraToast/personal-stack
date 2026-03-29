export interface AuthUser {
  sub: string
  username: string
  email: string
  roles?: string[]
}
