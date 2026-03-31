export interface AuthUser {
  sub: string
  username: string
  email: string
  firstName: string
  lastName: string
  roles?: string[]
}
